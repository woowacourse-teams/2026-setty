import { useLocalSearchParams } from 'expo-router';
import { IncomingDetailScreen } from '@/features/requests/IncomingDetailScreen';

/** 수신: /request/{deliveryId} 요청 단건 상세. */
export default function RequestDetailRoute() {
  const { deliveryId } = useLocalSearchParams<{ deliveryId: string }>();
  return <IncomingDetailScreen deliveryId={Number(deliveryId)} />;
}
