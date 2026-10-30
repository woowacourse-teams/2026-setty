import { formatCompactWon } from '../../model/furniture';

/** 배송비 예산 필터에서 고를 수 있는 값과 표시 문구. */
export const DeliveryFeeBudget = {
    // 기본 배송비보다 작은 예산은 서버가 항상 빈 목록을 주므로 슬라이더는 기본 배송비부터 시작한다
    MIN: 10_000,
    MAX: 100_000,
    STEP: 5_000,

    // 직접 고를 때 처음 보여줄 예산
    DEFAULT: 20_000,

    format(value: number): string {
        return formatCompactWon(value);
    }
};
