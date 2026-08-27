import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from 'react';
import {
    createListing,
    updateListing,
    type ListingCategory,
    type ListingDetail
} from '../api/listings';

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

function AddIcon() {
    return <span aria-hidden="true" className="furniture-registration__add-icon">+</span>;
}

type FurnitureRegistrationProps = {
    listing?: ListingDetail;
    onCancel: () => void;
    onSaved: () => void;
};

export function FurnitureRegistration({ listing, onCancel, onSaved }: FurnitureRegistrationProps) {
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

    useEffect(() => () => imageUrls.forEach((url) => URL.revokeObjectURL(url)), [imageUrls]);

    const canSubmit = Boolean(
        title.trim()
        && price
        && dimensions.width
        && dimensions.depth
        && dimensions.height
        && description.trim()
        && retainedImageIds.length + images.length >= 1
        && retainedImageIds.length + images.length <= 5
    );

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
        if (!canSubmit) return;

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
            onSaved();
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '가구 정보를 저장하지 못했습니다.');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <section aria-labelledby="furniture-registration-title" className="furniture-registration">
            <h1 id="furniture-registration-title">{listing ? '가구 수정' : '새 가구 등록'}</h1>

            <form onSubmit={(event) => void handleSubmit(event)}>
                <fieldset className="furniture-registration__field furniture-registration__field--images">
                    <legend>사진</legend>
                    <input
                        accept="image/*"
                        aria-label="가구 사진 선택"
                        className="furniture-registration__image-input"
                        id="furniture-registration-images"
                        multiple
                        onChange={changeImages}
                        type="file"
                    />
                    <div className="furniture-registration__image-list">
                        {listing?.images.filter((image) => retainedImageIds.includes(image.id)).map((image) => (
                            <span className="furniture-registration__image-slot" key={image.id}>
                                <img alt="기존 가구 사진" src={image.url} />
                                <button aria-label="기존 사진 삭제" className="furniture-registration__image-remove" onClick={() => removeExistingImage(image.id)} type="button">×</button>
                            </span>
                        ))}
                        {imageUrls.map((imageUrl, index) => (
                            <span className="furniture-registration__image-slot" key={imageUrl}>
                                <img alt={`선택한 가구 사진 ${index + 1}`} src={imageUrl} />
                                <button aria-label="새 사진 삭제" className="furniture-registration__image-remove" onClick={() => setImages((current) => current.filter((_, fileIndex) => fileIndex !== index))} type="button">×</button>
                            </span>
                        ))}
                        {retainedImageIds.length + images.length < 5 && (
                            <label className="furniture-registration__image-slot furniture-registration__image-slot--add" htmlFor="furniture-registration-images"><AddIcon /></label>
                        )}
                    </div>
                </fieldset>

                <label className="furniture-registration__field">
                    <span>제목</span>
                    <input
                        maxLength={100}
                        onChange={(event) => setTitle(event.target.value)}
                        placeholder="원목 4인 식탁"
                        type="text"
                        value={title}
                    />
                </label>

                <fieldset className="furniture-registration__field">
                    <legend>카테고리</legend>
                    <div aria-label="카테고리" className="furniture-registration__chips" role="radiogroup">
                        {categories.map((item) => (
                            <button
                                aria-checked={category === item.value}
                                className={`furniture-registration__chip ${category === item.value ? 'furniture-registration__chip--selected' : ''}`}
                                key={item.value}
                                onClick={() => setCategory(item.value)}
                                role="radio"
                                type="button"
                            >
                                {item.label}
                            </button>
                        ))}
                    </div>
                </fieldset>

                <fieldset className="furniture-registration__field">
                    <legend>상태</legend>
                    <div aria-label="가구 상태" className="furniture-registration__chips" role="radiogroup">
                        {conditionGrades.map((grade) => (
                            <button
                                aria-checked={conditionGrade === grade}
                                className={`furniture-registration__chip ${conditionGrade === grade ? 'furniture-registration__chip--selected' : ''}`}
                                key={grade}
                                onClick={() => setConditionGrade(grade)}
                                role="radio"
                                type="button"
                            >
                                {grade}급
                            </button>
                        ))}
                    </div>
                </fieldset>

                <fieldset className="furniture-registration__field">
                    <legend>크기 (cm)</legend>
                    <div className="furniture-registration__dimensions">
                        <label><span>W</span><input aria-label="가로" inputMode="numeric" min="1" onChange={(event) => changeDimension('width', event.target.value)} type="number" value={dimensions.width} /></label>
                        <i aria-hidden="true">×</i>
                        <label><span>D</span><input aria-label="세로" inputMode="numeric" min="1" onChange={(event) => changeDimension('depth', event.target.value)} type="number" value={dimensions.depth} /></label>
                        <i aria-hidden="true">×</i>
                        <label><span>H</span><input aria-label="높이" inputMode="numeric" min="1" onChange={(event) => changeDimension('height', event.target.value)} type="number" value={dimensions.height} /></label>
                    </div>
                    <p className="furniture-registration__hint">예상 배송비 크기 입력 필요</p>
                </fieldset>

                <label className="furniture-registration__field">
                    <span>가격</span>
                    <div className="furniture-registration__price-input"><b>₩</b><input aria-label="가격" inputMode="numeric" min="0" onChange={(event) => setPrice(event.target.value)} placeholder="180000" type="number" value={price} /></div>
                </label>

                <label className="furniture-registration__field furniture-registration__field--description">
                    <span>설명</span>
                    <textarea maxLength={1000} onChange={(event) => setDescription(event.target.value)} placeholder="사용 기간, 흠집, 분해 여부" value={description} />
                </label>

                {error && <p className="furniture-registration__error" role="alert">{error}</p>}
                <div className="furniture-registration__actions">
                    <button className="furniture-registration__cancel" onClick={onCancel} type="button">취소</button>
                    <button className="furniture-registration__submit" disabled={!canSubmit || isSubmitting} type="submit">{isSubmitting ? '저장 중...' : listing ? '수정하기' : '등록하기'}</button>
                </div>
            </form>
        </section>
    );
}
