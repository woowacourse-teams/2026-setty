import { DeliveryStatus } from '@/model/delivery';
import { colors } from '@/theme';

export interface StatusMeta {
  label: string;
  bg: string;
  color: string;
}

/**
 * 상태 코드 → 표시 라벨/색. (디자인 meta() 기준)
 * status는 코드로 내려오므로 앱이 표시를 소유한다(category와 달리 매핑 유지).
 * 알 수 없는 상태는 코드 원문 + 중립 색으로 안전하게 표시한다.
 */
const META: Record<DeliveryStatus, StatusMeta> = {
  REQUESTED: { label: '요청', bg: colors.pillNeutralBg, color: colors.teal },
  ACCEPTED: { label: '수령대기', bg: '#C9FDF2', color: colors.teal },
  PICKED_UP: { label: '배송중', bg: colors.gradientMid, color: colors.tealDeep },
  DELIVERED: { label: '완료', bg: '#B6F2D1', color: '#1C5E3A' },
};

export function statusMeta(status: DeliveryStatus | string): StatusMeta {
  return (
    META[status as DeliveryStatus] ?? {
      label: String(status),
      bg: colors.pillNeutralBg,
      color: colors.textMuted,
    }
  );
}
