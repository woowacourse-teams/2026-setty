import { formatWon } from '../../model/furniture';

/** 배송비 예산 필터에서 고를 수 있는 값과 표시 문구. */
export const DeliveryFeeBudget = {
    PRESETS: [10_000],

    // 기본 배송비보다 작은 예산은 서버가 항상 빈 목록을 주므로 슬라이더는 기본 배송비부터 시작한다
    MIN: 10_000,
    MAX: 100_000,
    STEP: 5_000,

    // 직접 고를 때 처음 보여줄 예산
    DEFAULT: 20_000,

    /** 15000 → "1.5만 원", 20000 → "2만 원", 만·천 단위로 떨어지지 않으면 "23,500원" */
    format(value: number): string {
        if (value % 1_000 !== 0) return formatWon(value);
        const man = Math.floor(value / 10_000);
        const thousand = Math.floor((value % 10_000) / 1_000);
        return thousand === 0 ? `${man}만 원` : `${man}.${thousand}만 원`;
    }
};
