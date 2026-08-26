import { Pressable, View } from 'react-native';
import { DeliveryRequestSummaryResponse } from '@/model/delivery';
import { formatFee, routeText } from '@/lib/format';
import { AppText } from '@/components/AppText';
import { styles } from './RequestCard.styles';

interface RequestCardProps {
  request: DeliveryRequestSummaryResponse;
  onPress: () => void;
  onAccept: () => void;
}

/** 요청 목록의 카드 1개. 카드 탭 → 상세, 우하단 버튼 → 즉시 수락. */
export function RequestCard({ request, onPress, onAccept }: RequestCardProps) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <View style={styles.topRow}>
        <View style={styles.left}>
          <View style={styles.categoryPill}>
            <AppText variant="bold" style={styles.categoryText}>
              {request.category}
            </AppText>
          </View>
          <AppText variant="display" style={styles.itemName} numberOfLines={1}>
            {request.itemName}
          </AppText>
        </View>
        <View style={styles.feeCol}>
          <AppText variant="medium" style={styles.feeLabel}>
            배송비
          </AppText>
          <AppText variant="display" style={styles.feeValue}>
            {formatFee(request.deliveryFee)}
          </AppText>
        </View>
      </View>

      <View style={styles.footer}>
        <View style={styles.routeDot} />
        <AppText variant="medium" style={styles.routeText} numberOfLines={1}>
          {routeText(request.pickupAddress, request.deliveryAddress)}
        </AppText>
        <Pressable
          onPress={onAccept}
          hitSlop={6}
          style={({ pressed }) => [styles.acceptBtn, pressed && styles.acceptBtnPressed]}
        >
          <AppText variant="bold" style={styles.acceptText}>
            수락하기
          </AppText>
        </Pressable>
      </View>
    </Pressable>
  );
}
