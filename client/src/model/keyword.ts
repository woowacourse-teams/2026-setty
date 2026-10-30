import { categoryLabel, formatWon, furnitureCategoryOf, type FurnitureCategory } from './furniture';

/** 키워드에 거는 조건. 모두 null이면 조건 없음. */
export type KeywordConditions = {
    category: FurnitureCategory | null;
    minPrice: number | null;
    maxPrice: number | null;
    maxDeliveryFee: number | null;
    maxTotalPrice: number | null;
    excludeKeywords: string[];
};

export type Keyword = {
    id: number;
    keyword: string;
    conditions: KeywordConditions;
    createdAt: string;
};

export const MAX_KEYWORDS = 10;
export const MAX_EXCLUDE_KEYWORDS = 10;
export const CONDITION_AMOUNT_MAX = 100_000_000;
export const KEYWORD_MAX_LENGTH = 20;

export const emptyConditions: KeywordConditions = {
    category: null,
    minPrice: null,
    maxPrice: null,
    maxDeliveryFee: null,
    maxTotalPrice: null,
    excludeKeywords: []
};

export function keywordCategoryOf(value: string | null | undefined): FurnitureCategory | null {
    if (!value) return null;
    const category = furnitureCategoryOf(value);
    return category === 'ETC' ? null : category;
}

export function hasConditions(conditions: KeywordConditions): boolean {
    const { excludeKeywords, ...rest } = conditions;
    return excludeKeywords.length > 0 || Object.values(rest).some((value) => value !== null);
}

/** 15000 → "1.5만원", 300000 → "30만원", 만 단위로 안 떨어지면 "23,500원" */
function formatMan(value: number): string {
    if (value < 10_000 || value % 1_000 !== 0) return formatWon(value);
    const man = value / 10_000;
    return `${Number.isInteger(man) ? man : man.toFixed(1)}만원`;
}

function priceRangeText(minPrice: number | null, maxPrice: number | null): string | null {
    if (minPrice !== null && maxPrice !== null) return `${formatMan(minPrice)}~${formatMan(maxPrice)}`;
    if (minPrice !== null) return `${formatMan(minPrice)} 이상`;
    if (maxPrice !== null) return `${formatMan(maxPrice)} 이하`;
    return null;
}

/** 목록에서 키워드 아래에 보여줄 조건 요약. 예: "소파 · 10만~30만원 · 배송비 2만원까지 · '케이스'·'중고' 제외" */
export function conditionsSummary(conditions: KeywordConditions): string {
    const parts = [
        conditions.category && categoryLabel(conditions.category),
        priceRangeText(conditions.minPrice, conditions.maxPrice),
        conditions.maxDeliveryFee !== null && `배송비 ${formatMan(conditions.maxDeliveryFee)}까지`,
        conditions.maxTotalPrice !== null && `총 ${formatMan(conditions.maxTotalPrice)}까지`,
        conditions.excludeKeywords.length > 0 && `${conditions.excludeKeywords.map((word) => `'${word}'`).join('·')} 제외`
    ].filter((part): part is string => typeof part === 'string');
    return parts.join(' · ');
}
