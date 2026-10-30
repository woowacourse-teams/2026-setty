import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router';
import { isMemberDongRequired, isUnauthorized, toLoadError, toUserMessage, type LoadState } from '../../api/errors';
import { furnitureRepository } from '../../api/repositories';
import {
    categoryLabel,
    conditionGrades,
    selectableCategories,
    type FurnitureCategory,
    type ListingDraft,
    type ListingImage
} from '../../model/furniture';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    BackIconButton,
    BottomBar,
    Button,
    ErrorContent,
    FlexibleTopAppBar,
    IconButton,
    LoadingContent,
    LoadingIndicator,
    Scaffold,
    SectionTitle,
    TextField,
    ToggleButtonGroup
} from '../../ui/components';
import { Icon } from '../../ui/Icon';
import { showSnackbar } from '../../ui/snackbar';
import styles from './editor.module.css';

const MAX_IMAGE_COUNT = 5;
const TITLE_MAX_LENGTH = 100;
const DESCRIPTION_MAX_LENGTH = 1_000;
const PRICE_MAX = 100_000_000;
// 최대 금액(1억)보다 한 자리 더 받아 범위 초과를 알려준다
const PRICE_MAX_DIGITS = 10;
const DIMENSION_MAX_DIGITS = 5;
const DIMENSION_MIN = 1;
const DIMENSION_MAX = 1_000;
const ACCEPTED_IMAGE_TYPES = 'image/jpeg,image/png,image/webp';

type NewImage = {
    file: File;
    previewUrl: string;
};

type EditorForm = {
    title: string;
    description: string;
    // 숫자만 저장한다
    price: string;
    category: FurnitureCategory | null;
    conditionGrade: string | null;
    widthCm: string;
    depthCm: string;
    heightCm: string;
    // 수정 시 유지할 기존 이미지. 이 순서대로 재정렬된다
    retainedImages: ListingImage[];
    newImages: NewImage[];
};

const emptyForm: EditorForm = {
    title: '',
    description: '',
    price: '',
    category: null,
    conditionGrade: null,
    widthCm: '',
    depthCm: '',
    heightCm: '',
    retainedImages: [],
    newImages: []
};

function digits(value: string, maxLength: number): string {
    return value.replace(/\D/g, '').slice(0, maxLength);
}

function isDimensionInvalid(value: string): boolean {
    if (!value) return false;
    const number = Number(value);
    return !(number >= DIMENSION_MIN && number <= DIMENSION_MAX);
}

function validate(form: EditorForm) {
    const imageCount = form.retainedImages.length + form.newImages.length;
    const titleError = form.title.length > TITLE_MAX_LENGTH;
    const descriptionError = form.description.length > DESCRIPTION_MAX_LENGTH;
    const priceError = form.price.length > 0 && Number(form.price) > PRICE_MAX;
    const widthError = isDimensionInvalid(form.widthCm);
    const depthError = isDimensionInvalid(form.depthCm);
    const heightError = isDimensionInvalid(form.heightCm);
    const isComplete = form.title.trim().length > 0 && !titleError
        && form.description.trim().length > 0 && !descriptionError
        && form.price.length > 0 && !priceError
        && form.category !== null && form.conditionGrade !== null
        && [form.widthCm, form.depthCm, form.heightCm].every(Boolean)
        && !widthError && !depthError && !heightError
        && imageCount >= 1 && imageCount <= MAX_IMAGE_COUNT;
    return { imageCount, titleError, descriptionError, priceError, widthError, depthError, heightError, isComplete };
}

function toDraft(form: EditorForm): ListingDraft {
    return {
        title: form.title.trim(),
        description: form.description.trim(),
        price: Number(form.price),
        category: form.category!,
        conditionGrade: form.conditionGrade!,
        widthCm: Number(form.widthCm),
        depthCm: Number(form.depthCm),
        heightCm: Number(form.heightCm)
    };
}

/** 매물 등록·수정 화면. 주소에 매물 id가 있으면 해당 매물을 수정한다. */
export function ListingEditorScreen() {
    const { listingId: listingIdParam } = useParams();
    const listingId = listingIdParam === undefined ? null : Number(listingIdParam);
    const isEditing = listingId !== null;
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    // 수정할 매물을 불러오는 상태. 등록일 때는 바로 성공
    const [loadState, setLoadState] = useState<LoadState<null>>(isEditing ? { status: 'loading' } : { status: 'success', data: null });
    const [form, setForm] = useState<EditorForm>(emptyForm);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const validation = useMemo(() => validate(form), [form]);
    const canSubmit = !isSubmitting && loadState.status === 'success' && validation.isComplete;
    const remainingImageSlots = Math.max(0, MAX_IMAGE_COUNT - validation.imageCount);

    const load = useCallback(() => {
        if (listingId === null) return;
        setLoadState({ status: 'loading' });
        furnitureRepository.getEditableListing(listingId)
            .then((listing) => {
                const { draft } = listing;
                setForm({
                    title: draft.title,
                    description: draft.description,
                    price: String(draft.price),
                    category: draft.category,
                    conditionGrade: draft.conditionGrade,
                    widthCm: String(draft.widthCm),
                    depthCm: String(draft.depthCm),
                    heightCm: String(draft.heightCm),
                    retainedImages: listing.images,
                    newImages: []
                });
                setLoadState({ status: 'success', data: null });
            })
            .catch((error: unknown) => setLoadState(toLoadError(error)));
    }, [listingId]);

    useEffect(load, [load]);

    // 미리보기로 만든 주소는 화면을 떠날 때 해제한다
    const newImagesRef = useRef(form.newImages);
    newImagesRef.current = form.newImages;
    useEffect(() => () => newImagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl)), []);

    const update = (patch: Partial<EditorForm>) => setForm((current) => ({ ...current, ...patch }));

    /** 남은 자리만큼만 추가한다. 이미 고른 사진은 다시 추가하지 않는다. */
    const addImages = (files: FileList | null) => {
        if (!files) return;
        setForm((current) => {
            const slots = Math.max(0, MAX_IMAGE_COUNT - current.retainedImages.length - current.newImages.length);
            const isPicked = (file: File) => current.newImages.some(({ file: picked }) =>
                picked.name === file.name && picked.size === file.size && picked.lastModified === file.lastModified);
            const added = Array.from(files)
                .filter((file) => !isPicked(file))
                .slice(0, slots)
                .map((file) => ({ file, previewUrl: URL.createObjectURL(file) }));
            return { ...current, newImages: [...current.newImages, ...added] };
        });
    };

    const removeNewImage = (image: NewImage) => {
        URL.revokeObjectURL(image.previewUrl);
        setForm((current) => ({ ...current, newImages: current.newImages.filter((item) => item !== image) }));
    };

    const submit = async () => {
        if (!canSubmit) return;
        setIsSubmitting(true);
        try {
            const draft = toDraft(form);
            const files = form.newImages.map((image) => image.file);
            if (listingId === null) {
                const createdId = await furnitureRepository.createListing(draft, files);
                // 새로 등록한 매물은 상세 화면으로 이어서 보여준다
                navigate(routes.furnitureDetail(createdId), { replace: true });
            } else {
                await furnitureRepository.updateListing(listingId, draft, form.retainedImages.map((image) => image.id), files);
                navigateBack();
            }
        } catch (error) {
            setIsSubmitting(false);
            if (isUnauthorized(error)) {
                navigateToLogin();
            } else if (isMemberDongRequired(error)) {
                // 판매자 행정동이 없어 등록할 수 없다
                navigate(routes.profileEdit);
            } else {
                showSnackbar(toUserMessage(error, '가구를 저장하지 못했습니다'));
            }
        }
    };

    return (
        <Scaffold
            bottomBar={loadState.status === 'success' && (
                <BottomBar>
                    {isSubmitting ? <LoadingIndicator /> : (
                        <Button disabled={!canSubmit} fullWidth onClick={() => void submit()} size="medium">
                            {isEditing ? '수정 완료' : '등록하기'}
                        </Button>
                    )}
                </BottomBar>
            )}
            topBar={(
                <FlexibleTopAppBar
                    navigation={<BackIconButton disabled={isSubmitting} onBack={navigateBack} />}
                    size="medium"
                    subtitle="배송비는 가구 크기로 자동 계산돼요"
                    title={isEditing ? '가구 수정' : '가구 등록'}
                />
            )}
        >
            {loadState.status === 'loading' && <LoadingContent />}
            {loadState.status === 'error' && (
                <ErrorContent isUnauthorized={loadState.isUnauthorized} message={loadState.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {loadState.status === 'success' && (
                <div className={styles.form}>
                    <div className={styles.section}>
                        <SectionTitle style={{ padding: '0 16px' }}>사진 {validation.imageCount}/{MAX_IMAGE_COUNT}</SectionTitle>
                        <div className={styles['image-row']}>
                            <button
                                className={styles['add-tile']}
                                disabled={remainingImageSlots === 0 || isSubmitting}
                                onClick={() => fileInputRef.current?.click()}
                                type="button"
                            >
                                <Icon label="사진 추가" name="addPhoto" />
                                <span className="type-label-medium">사진 추가</span>
                            </button>
                            {/* 기존 이미지 뒤에 새 이미지가 붙으므로 같은 순서로 보여준다 */}
                            {form.retainedImages.map((image, index) => (
                                <ImageTile
                                    isCover={index === 0}
                                    key={`retained-${image.id}`}
                                    onRemove={() => update({ retainedImages: form.retainedImages.filter((item) => item.id !== image.id) })}
                                    src={image.url}
                                />
                            ))}
                            {form.newImages.map((image, index) => (
                                <ImageTile
                                    isCover={form.retainedImages.length === 0 && index === 0}
                                    key={image.previewUrl}
                                    onRemove={() => removeNewImage(image)}
                                    src={image.previewUrl}
                                />
                            ))}
                        </div>
                        <input
                            accept={ACCEPTED_IMAGE_TYPES}
                            hidden
                            multiple
                            onChange={(event) => {
                                addImages(event.target.files);
                                // 같은 사진을 지운 뒤 다시 고를 수 있게 선택을 비운다
                                event.target.value = '';
                            }}
                            ref={fileInputRef}
                            type="file"
                        />
                        <p className={`${styles.hint} type-label-medium`}>
                            첫 번째 사진이 대표 사진이 돼요. JPEG·PNG·WebP, 전체 25MB까지 올릴 수 있어요.
                        </p>
                    </div>
                    <FormSection title="기본 정보">
                        <TextField
                            enterKeyHint="next"
                            isError={validation.titleError}
                            label="제목"
                            onChange={(title) => update({ title })}
                            supportingText={`${form.title.length}/${TITLE_MAX_LENGTH}`}
                            value={form.title}
                        />
                        <TextField
                            isError={validation.descriptionError}
                            label="상세 설명"
                            multiline
                            onChange={(description) => update({ description })}
                            placeholder="사용 기간, 하자 여부 등을 알려주세요"
                            supportingText={`${form.description.length}/${DESCRIPTION_MAX_LENGTH}`}
                            value={form.description}
                        />
                    </FormSection>
                    <FormSection title="가격">
                        <TextField
                            enterKeyHint="next"
                            inputMode="numeric"
                            isError={validation.priceError}
                            label="판매 가격"
                            onChange={(price) => update({ price: digits(price, PRICE_MAX_DIGITS) })}
                            suffix="원"
                            supportingText={validation.priceError ? '가격은 1억 원 이하로 입력해 주세요' : undefined}
                            value={form.price}
                        />
                    </FormSection>
                    <FormSection bleed title="카테고리">
                        <ToggleButtonGroup
                            ariaLabel="카테고리"
                            labelOf={categoryLabel}
                            onSelect={(category) => update({ category })}
                            options={selectableCategories}
                            selected={form.category}
                            style={{ padding: '0 16px' }}
                        />
                    </FormSection>
                    <FormSection title="상태 등급">
                        <ToggleButtonGroup
                            ariaLabel="상태 등급"
                            fill
                            labelOf={(grade) => `${grade}급`}
                            onSelect={(conditionGrade) => update({ conditionGrade })}
                            options={conditionGrades}
                            selected={form.conditionGrade as (typeof conditionGrades)[number] | null}
                        />
                    </FormSection>
                    <FormSection title="크기">
                        <div className={styles['dimension-row']}>
                            <TextField
                                enterKeyHint="next"
                                inputMode="numeric"
                                isError={validation.widthError}
                                label="가로"
                                onChange={(value) => update({ widthCm: digits(value, DIMENSION_MAX_DIGITS) })}
                                suffix="cm"
                                value={form.widthCm}
                            />
                            <TextField
                                enterKeyHint="next"
                                inputMode="numeric"
                                isError={validation.depthError}
                                label="깊이"
                                onChange={(value) => update({ depthCm: digits(value, DIMENSION_MAX_DIGITS) })}
                                suffix="cm"
                                value={form.depthCm}
                            />
                            <TextField
                                enterKeyHint="done"
                                inputMode="numeric"
                                isError={validation.heightError}
                                label="높이"
                                onChange={(value) => update({ heightCm: digits(value, DIMENSION_MAX_DIGITS) })}
                                suffix="cm"
                                value={form.heightCm}
                            />
                        </div>
                        <span
                            className="type-label-medium"
                            style={{
                                color: validation.widthError || validation.depthError || validation.heightError
                                    ? 'var(--error)'
                                    : 'var(--on-surface-variant)'
                            }}
                        >
                            각 변은 1~1,000cm 사이로 입력해 주세요
                        </span>
                    </FormSection>
                </div>
            )}
        </Scaffold>
    );
}

function FormSection({ title, bleed = false, children }: { title: string; bleed?: boolean; children: ReactNode }) {
    return (
        <section className={styles.section}>
            <SectionTitle style={{ padding: '0 16px' }}>{title}</SectionTitle>
            <div className={`${styles.section__content} ${bleed ? styles['section__content--bleed'] : ''}`}>{children}</div>
        </section>
    );
}

function ImageTile({ src, isCover, onRemove }: { src: string; isCover: boolean; onRemove: () => void }) {
    return (
        <div className={styles['image-tile']}>
            <img alt="" className={styles['image-tile__image']} src={src} />
            {isCover && <span className={`${styles['image-tile__cover']} type-label-medium`}>대표</span>}
            <span className={styles['image-tile__remove']}>
                <IconButton icon="close" iconSize={16} label="사진 삭제" onClick={onRemove} small variant="tonal" />
            </span>
        </div>
    );
}
