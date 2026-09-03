import { Plus } from '@phosphor-icons/react/dist/icons/Plus';
import { X } from '@phosphor-icons/react/dist/icons/X';
import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import {
    createListing,
    updateListing,
    type ListingCategory,
    type ListingDetail
} from '../api/listings';
import furnitureRegistrationStyles from '../styles/modules/FurnitureRegistration.module.css';

const categories = [
    { value: 'SOFA', label: '소파' },
    { value: 'TABLE', label: '테이블' },
    { value: 'DESK', label: '책상' },
    { value: 'CHAIR', label: '의자' },
    { value: 'STORAGE', label: '수납' },
    { value: 'BED', label: '침대' }
] as const;

const conditionGrades = ['S', 'A', 'B', 'C'] as const;

type Dimensions = {
    width: string;
    depth: string;
    height: string;
};

type FurnitureRegistrationProps = {
    listing?: ListingDetail;
    onCancel: () => void;
    onDirtyChange: (isDirty: boolean) => void;
    onSaved: () => void;
};

type InitialFormValues = {
    images: File[];
    retainedImageIds: number[];
    title: string;
    category: ListingCategory;
    conditionGrade: (typeof conditionGrades)[number];
    dimensions: Dimensions;
    price: string;
    description: string;
};

function haveSameItems<T>(current: T[], initial: T[]) {
    return current.length === initial.length
        && current.every((item, index) => item === initial[index]);
}

function toDigits(value: string) {
    return value.replace(/\D/g, '');
}

function groupThousands(value: string) {
    return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

export function FurnitureRegistration({ listing, onCancel, onDirtyChange, onSaved }: FurnitureRegistrationProps) {
    const onDirtyChangeRef = useRef(onDirtyChange);
    const imageInputRef = useRef<HTMLInputElement>(null);
    const initialFormValuesRef = useRef<InitialFormValues>({
        images: [],
        retainedImageIds: listing?.images.map((image) => image.id) ?? [],
        title: listing?.title ?? '',
        category: listing?.category ?? 'TABLE',
        conditionGrade: listing?.conditionGrade ?? 'A',
        dimensions: {
            width: listing?.dimensions.widthCm.toString() ?? '',
            depth: listing?.dimensions.depthCm.toString() ?? '',
            height: listing?.dimensions.heightCm.toString() ?? ''
        },
        price: listing?.price.toString() ?? '',
        description: listing?.description ?? ''
    });
    const [images, setImages] = useState<File[]>([]);
    const [retainedImageIds, setRetainedImageIds] = useState(() => listing?.images.map((image) => image.id) ?? []);
    const [title, setTitle] = useState(listing?.title ?? '');
    const [category, setCategory] = useState<ListingCategory>(listing?.category ?? 'TABLE');
    const [conditionGrade, setConditionGrade] = useState<(typeof conditionGrades)[number]>(listing?.conditionGrade ?? 'A');
    const [dimensions, setDimensions] = useState<Dimensions>(() => ({
        width: listing?.dimensions.widthCm.toString() ?? '',
        depth: listing?.dimensions.depthCm.toString() ?? '',
        height: listing?.dimensions.heightCm.toString() ?? ''
    }));
    const [price, setPrice] = useState(listing?.price.toString() ?? '');
    const [description, setDescription] = useState(listing?.description ?? '');
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const imageUrls = useMemo(() => images.map((image) => URL.createObjectURL(image)), [images]);
    const initialFormValues = initialFormValuesRef.current;
    const isDirty = title !== initialFormValues.title
        || category !== initialFormValues.category
        || conditionGrade !== initialFormValues.conditionGrade
        || dimensions.width !== initialFormValues.dimensions.width
        || dimensions.depth !== initialFormValues.dimensions.depth
        || dimensions.height !== initialFormValues.dimensions.height
        || price !== initialFormValues.price
        || description !== initialFormValues.description
        || !haveSameItems(retainedImageIds, initialFormValues.retainedImageIds)
        || !haveSameItems(images, initialFormValues.images);

    useEffect(() => () => imageUrls.forEach((url) => URL.revokeObjectURL(url)), [imageUrls]);

    useEffect(() => {
        onDirtyChangeRef.current = onDirtyChange;
        onDirtyChange(isDirty);
    }, [isDirty, onDirtyChange]);

    useEffect(() => () => onDirtyChangeRef.current(false), []);

    const changeImages = (event: ChangeEvent<HTMLInputElement>) => {
        const selectedImages = Array.from(event.target.files ?? []);
        setImages((current) => [...current, ...selectedImages].slice(0, 5 - retainedImageIds.length));
        event.target.value = '';
    };

    const changeDimension = (dimension: keyof Dimensions, value: string) => {
        setDimensions((current) => ({ ...current, [dimension]: value }));
    };

    const removeExistingImage = (imageId: number) => {
        setRetainedImageIds((current) => current.filter((id) => id !== imageId));
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (retainedImageIds.length + images.length < 1) {
            setError('가구 사진을 1장 이상 선택해 주세요.');
            imageInputRef.current?.focus();
            return;
        }

        setError(null);
        setIsSubmitting(true);

        const payload = {
            title: title.trim(),
            description: description.trim(),
            price: Number(price),
            category,
            conditionGrade,
            dimensions: {
                widthCm: Number(dimensions.width),
                depthCm: Number(dimensions.depth),
                heightCm: Number(dimensions.height)
            }
        };

        try {
            if (listing) {
                await updateListing(listing.id, payload, retainedImageIds, images);
            } else {
                await createListing(payload, images);
            }
            onDirtyChange(false);
            onSaved();
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '가구 정보를 저장하지 못했습니다.');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <section aria-labelledby="furniture-registration-title" className={furnitureRegistrationStyles['furniture-registration']}>
            <h1 id="furniture-registration-title">{listing ? '가구 수정' : '새 가구 등록'}</h1>

            <form aria-busy={isSubmitting} onSubmit={(event) => void handleSubmit(event)}>
                <fieldset className={[furnitureRegistrationStyles['furniture-registration__field'], furnitureRegistrationStyles['furniture-registration__field--images']].filter(Boolean).join(' ')}>
                    <legend>사진</legend>
                    <p className={furnitureRegistrationStyles['furniture-registration__hint']} id="furniture-registration-images-hint">가구의 상태를 확인할 수 있는 사진을 1장 이상, 최대 5장 선택해 주세요.</p>
                    <div className={furnitureRegistrationStyles['furniture-registration__image-list']}>
                        {listing?.images.filter((image) => retainedImageIds.includes(image.id)).map((image, index) => (
                            <div className={furnitureRegistrationStyles['furniture-registration__image-slot']} key={image.id}>
                                <img alt={`${index + 1}번째 기존 가구 사진`} src={image.url} />
                                <button aria-label={`${index + 1}번째 기존 사진 삭제`} className={furnitureRegistrationStyles['furniture-registration__image-remove']} onClick={() => removeExistingImage(image.id)} type="button">
                                    <X aria-hidden="true" weight="bold" />
                                </button>
                            </div>
                        ))}
                        {imageUrls.map((imageUrl, index) => (
                            <div className={furnitureRegistrationStyles['furniture-registration__image-slot']} key={imageUrl}>
                                <img alt={`선택한 가구 사진 ${index + 1}`} src={imageUrl} />
                                <button aria-label={`${index + 1}번째 새 사진 삭제`} className={furnitureRegistrationStyles['furniture-registration__image-remove']} onClick={() => setImages((current) => current.filter((_, fileIndex) => fileIndex !== index))} type="button">
                                    <X aria-hidden="true" weight="bold" />
                                </button>
                            </div>
                        ))}
                        {retainedImageIds.length + images.length < 5 && (
                            <label className={[furnitureRegistrationStyles['furniture-registration__image-slot'], furnitureRegistrationStyles['furniture-registration__image-slot--add']].filter(Boolean).join(' ')}>
                                <input
                                    accept="image/*"
                                    aria-label="가구 사진 추가"
                                    aria-describedby="furniture-registration-images-hint"
                                    className={furnitureRegistrationStyles['furniture-registration__image-input']}
                                    multiple
                                    onChange={changeImages}
                                    ref={imageInputRef}
                                    type="file"
                                />
                                <Plus aria-hidden="true" className={furnitureRegistrationStyles['furniture-registration__add-icon']} weight="regular" />
                            </label>
                        )}
                    </div>
                </fieldset>

                <label className={furnitureRegistrationStyles['furniture-registration__field']}>
                    <span>제목</span>
                    <input
                        maxLength={100}
                        name="title"
                        onChange={(event) => setTitle(event.target.value)}
                        placeholder="원목 4인 식탁"
                        required
                        type="text"
                        value={title}
                    />
                </label>

                <fieldset className={furnitureRegistrationStyles['furniture-registration__field']}>
                    <legend>카테고리</legend>
                    <div className={furnitureRegistrationStyles['furniture-registration__chips']}>
                        {categories.map((item) => (
                            <label
                                className={[furnitureRegistrationStyles['furniture-registration__chip'], category === item.value && furnitureRegistrationStyles['furniture-registration__chip--selected']].filter(Boolean).join(' ')}
                                key={item.value}
                            >
                                <input
                                    checked={category === item.value}
                                    className={furnitureRegistrationStyles['furniture-registration__choice-input']}
                                    name="category"
                                    onChange={() => setCategory(item.value)}
                                    type="radio"
                                    value={item.value}
                                />
                                <span>{item.label}</span>
                            </label>
                        ))}
                    </div>
                </fieldset>

                <fieldset className={furnitureRegistrationStyles['furniture-registration__field']}>
                    <legend>상태</legend>
                    <div className={furnitureRegistrationStyles['furniture-registration__chips']}>
                        {conditionGrades.map((grade) => (
                            <label
                                className={[furnitureRegistrationStyles['furniture-registration__chip'], conditionGrade === grade && furnitureRegistrationStyles['furniture-registration__chip--selected']].filter(Boolean).join(' ')}
                                key={grade}
                            >
                                <input
                                    checked={conditionGrade === grade}
                                    className={furnitureRegistrationStyles['furniture-registration__choice-input']}
                                    name="conditionGrade"
                                    onChange={() => setConditionGrade(grade)}
                                    type="radio"
                                    value={grade}
                                />
                                <span>{grade}급</span>
                            </label>
                        ))}
                    </div>
                </fieldset>

                <fieldset className={furnitureRegistrationStyles['furniture-registration__field']}>
                    <legend>크기 (cm)</legend>
                    <div className={furnitureRegistrationStyles['furniture-registration__dimensions']}>
                        <label><span aria-hidden="true">W</span><input aria-describedby="furniture-registration-dimensions-hint" aria-label="가로, 센티미터" inputMode="numeric" name="width" onChange={(event) => changeDimension('width', toDigits(event.target.value))} pattern="[0-9]*" required type="text" value={dimensions.width} /></label>
                        <i aria-hidden="true">×</i>
                        <label><span aria-hidden="true">D</span><input aria-describedby="furniture-registration-dimensions-hint" aria-label="세로, 센티미터" inputMode="numeric" name="depth" onChange={(event) => changeDimension('depth', toDigits(event.target.value))} pattern="[0-9]*" required type="text" value={dimensions.depth} /></label>
                        <i aria-hidden="true">×</i>
                        <label><span aria-hidden="true">H</span><input aria-describedby="furniture-registration-dimensions-hint" aria-label="높이, 센티미터" inputMode="numeric" name="height" onChange={(event) => changeDimension('height', toDigits(event.target.value))} pattern="[0-9]*" required type="text" value={dimensions.height} /></label>
                    </div>
                    <p className={furnitureRegistrationStyles['furniture-registration__hint']} id="furniture-registration-dimensions-hint">예상 배송비 계산을 위해 가로, 세로, 높이를 모두 입력해 주세요.</p>
                </fieldset>

                <label className={furnitureRegistrationStyles['furniture-registration__field']}>
                    <span>가격</span>
                    <div className={furnitureRegistrationStyles['furniture-registration__price-input']}><span aria-hidden="true">₩</span><input inputMode="numeric" name="price" onChange={(event) => setPrice(toDigits(event.target.value))} pattern="[0-9]*" placeholder="180,000" required type="text" value={groupThousands(price)} /></div>
                </label>

                <label className={[furnitureRegistrationStyles['furniture-registration__field'], furnitureRegistrationStyles['furniture-registration__field--description']].filter(Boolean).join(' ')}>
                    <span>설명</span>
                    <textarea maxLength={1000} name="description" onChange={(event) => setDescription(event.target.value)} placeholder="사용 기간, 흠집, 분해 여부" required value={description} />
                </label>

                {error && <p className={furnitureRegistrationStyles['furniture-registration__error']} role="alert">{error}</p>}
                <div className={furnitureRegistrationStyles['furniture-registration__actions']}>
                    <button className={furnitureRegistrationStyles['furniture-registration__cancel']} onClick={onCancel} type="button">취소</button>
                    <button className={furnitureRegistrationStyles['furniture-registration__submit']} disabled={isSubmitting} type="submit">{isSubmitting ? '저장 중...' : listing ? '수정하기' : '등록하기'}</button>
                </div>
            </form>
        </section>
    );
}
