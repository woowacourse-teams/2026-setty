import { DispatchStatus } from './api/operatorDispatchApi';

const DISPATCH_STATUS_LABELS: Record<DispatchStatus, string> = {
  SELLER_INPUT_PENDING: '판매자 입력 대기',
  FINAL_REVIEW_PENDING: '최종 검토 대기',
  FINAL_AMOUNT_CONFIRM_PENDING: '최종 금액 확인 대기',
  DISPATCH_PENDING: '배차 대기',
  DISPATCH_COMPLETED: '배차 완료',
  IN_TRANSIT: '운송 중',
  DELIVERY_COMPLETED: '배송 완료',
  FINAL_AMOUNT_REJECTED: '최종 금액 거절',
  TRANSPORT_INFEASIBLE: '운송 불가',
  USER_CANCELLED: '사용자 취소',
  DISPATCH_FAILED: '배차 실패',
};

export function getDispatchStatusLabel(status: DispatchStatus): string {
  return DISPATCH_STATUS_LABELS[status];
}

export function formatKoreanDateTime(value: string | null): string {
  if (!value) {
    return '미기록';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Seoul',
  }).format(date);
}

export function formatAmount(amount: number | null): string {
  return amount === null ? '미기록' : `${amount.toLocaleString('ko-KR')}원`;
}

export function formatOptionalText(value: string | null): string {
  return value?.trim() || '미기록';
}
