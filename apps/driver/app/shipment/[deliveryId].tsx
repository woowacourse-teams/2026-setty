import { useLocalSearchParams } from 'expo-router';
import { ShipmentDetailScreen } from '@/features/shipments/ShipmentDetailScreen';

/** 내 배차 상세: /shipment/{deliveryId}. */
export default function ShipmentDetailRoute() {
  const { deliveryId } = useLocalSearchParams<{ deliveryId: string }>();
  return <ShipmentDetailScreen deliveryId={Number(deliveryId)} />;
}
