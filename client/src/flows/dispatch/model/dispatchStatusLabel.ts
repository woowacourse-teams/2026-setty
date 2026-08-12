import type { DispatchStatus } from './dispatchTypes';

/**
 * 내부 상태와 사용자 표시 문구를 분리한다.
 * 문구는 `docs/product/user-operation-flow.md`의 배차 요청 상태표를 따른다.
 */
const DISPATCH_STATUS_LABEL: Record<DispatchStatus, string> = {
  SELLER_INPUT_PENDING: '판매자 정보를 기다리고 있어요',
  FINAL_REVIEW_PENDING: '배송 조건을 확인하고 있어요',
  FINAL_AMOUNT_CONFIRM_PENDING: '최종 금액 확인이 필요해요',
  DISPATCH_PENDING: '배차를 준비하고 있어요',
  DISPATCH_COMPLETED: '기사가 배정됐어요',
  IN_TRANSIT: '배송이 진행 중이에요',
  DELIVERY_COMPLETED: '배송이 완료됐어요',
  FINAL_AMOUNT_REJECTED: '최종 금액을 거절해 종료됐어요',
  TRANSPORT_INFEASIBLE: '현재 조건으로 진행하기 어려워요',
  USER_CANCELLED: '요청이 취소됐어요',
  DISPATCH_FAILED: '배차가 어려워 운영자가 안내할 예정이에요',
};

export const toDispatchStatusLabel = (status: DispatchStatus): string =>
  DISPATCH_STATUS_LABEL[status];
