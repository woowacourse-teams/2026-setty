import { formatCompactWon } from '../../model/furniture';

/** 물건 가격 필터. 최소·최대 중 한쪽만 있어도 된다. */
export type PriceRange = { min: number | null; max: number | null };

export const PriceFilter = {
    // 시트에서 바로 고르는 상한
    PRESETS: [50_000, 100_000, 200_000],
    // 등록 상한과 같다
    MAX: 100_000_000,
    MAX_DIGITS: 9,

    /** "5만 원 이하", "5만 원 이상", "5만~20만 원", "5,000~20만 원" */
    label(range: PriceRange): string {
        if (range.min !== null && range.max !== null) return `${formatCompactWon(range.min).replace(/ ?원$/, '')}~${formatCompactWon(range.max)}`;
        if (range.max !== null) return `${formatCompactWon(range.max)} 이하`;
        return `${formatCompactWon(range.min ?? 0)} 이상`;
    },

    equals(a: PriceRange | null, b: PriceRange | null): boolean {
        return a?.min === b?.min && a?.max === b?.max;
    }
};
