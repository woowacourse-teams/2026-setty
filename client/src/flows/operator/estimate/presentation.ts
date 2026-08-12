import { EstimateRequestStatus } from './api/operatorEstimateApi';

export function getEstimateStatusLabel(status: EstimateRequestStatus): string {
  return status === 'PENDING_REVIEW' ? '검토 대기' : '안내 완료';
}

export function formatKoreanDateTime(value: string): string {
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

export function formatOperatorPhoneNumber(phoneNumber: string): string {
  const digits = phoneNumber.replace(/\D/g, '');
  if (!/^010\d{8}$/.test(digits)) {
    return phoneNumber;
  }

  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}
