import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from 'react';

const categories = [
    { value: 'SOFA', label: '소파' },
    { value: 'TABLE', label: '테이블' },
    { value: 'DESK', label: '책상' },
    { value: 'CHAIR', label: '의자' },
    { value: 'STORAGE', label: '수납' },
    { value: 'BED', label: '침대' }
] as const;

const conditionGrades = ['S', 'A', 'B'] as const;

type Dimensions = {
    width: string;
    depth: string;
    height: string;
};

function AddIcon() {
    return <span aria-hidden="true" className="furniture-registration__add-icon">+</span>;
}

export function FurnitureRegistration() {
    const [images, setImages] = useState<File[]>([]);
    const [title, setTitle] = useState('');
    const [category, setCategory] = useState<(typeof categories)[number]['value']>('TABLE');
    const [conditionGrade, setConditionGrade] = useState<(typeof conditionGrades)[number]>('A');
    const [dimensions, setDimensions] = useState<Dimensions>({ width: '', depth: '', height: '' });
    const [price, setPrice] = useState('');
    const [description, setDescription] = useState('');

    const imageUrls = useMemo(() => images.map((image) => URL.createObjectURL(image)), [images]);

    useEffect(() => () => imageUrls.forEach((url) => URL.revokeObjectURL(url)), [imageUrls]);

    const canSubmit = Boolean(
        title.trim()
        && price
        && dimensions.width
        && dimensions.depth
        && dimensions.height
    );

    const changeImages = (event: ChangeEvent<HTMLInputElement>) => {
        const selectedImages = Array.from(event.target.files ?? []).slice(0, 4);
        setImages(selectedImages);
    };

    const changeDimension = (dimension: keyof Dimensions, value: string) => {
        setDimensions((current) => ({ ...current, [dimension]: value }));
    };

    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
    };

    return (
        <section aria-labelledby="furniture-registration-title" className="furniture-registration">
            <h1 id="furniture-registration-title">새 가구 등록</h1>

            <form onSubmit={handleSubmit}>
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
                    <label className="furniture-registration__image-list" htmlFor="furniture-registration-images">
                        {Array.from({ length: 4 }, (_, index) => {
                            const imageUrl = imageUrls[index];
                            const isFirstEmptySlot = index === 0 && !imageUrl;

                            return (
                                <span className="furniture-registration__image-slot" key={index}>
                                    {imageUrl ? <img alt={`선택한 가구 사진 ${index + 1}`} src={imageUrl} /> : isFirstEmptySlot && <AddIcon />}
                                </span>
                            );
                        })}
                    </label>
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
                    <legend>크기 (mm)</legend>
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
                    <span>설명 <em>선택</em></span>
                    <textarea maxLength={1000} onChange={(event) => setDescription(event.target.value)} placeholder="사용 기간, 흠집, 분해 여부" value={description} />
                </label>

                <button className="furniture-registration__submit" disabled={!canSubmit} type="submit">등록하기</button>
            </form>
        </section>
    );
}
