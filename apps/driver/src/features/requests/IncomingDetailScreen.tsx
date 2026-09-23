import { useCallback } from 'react';
import { ActivityIndicator, Pressable, ScrollView, View } from 'react-native';
import { useRouter } from 'expo-router';
import { formatFee, shortAddress } from '@/lib/format';
import { AppText } from '@/components/AppText';
import { Screen } from '@/components/Screen';
import { PrimaryButton } from '@/components/PrimaryButton';
import { RouteLine } from '@/components/RouteLine';
import { colors } from '@/theme';
import { useRequestDetail } from './useRequestDetail';
import { rejectedStore } from './rejectedStore';
import { styles } from './IncomingDetailScreen.styles';

/** 수신: 배차 요청 단건 상세 + 수락/거절. */
export function IncomingDetailScreen({ deliveryId }: { deliveryId: number }) {
  const router = useRouter();
  const { detail, loading, accepting, error, accept } = useRequestDetail(deliveryId);

  const goBack = useCallback(() => router.back(), [router]);

  const onAccept = useCallback(async () => {
    const ok = await accept();
    if (ok) router.back(); // 홈이 포커스되며 목록을 재조회한다
  }, [accept, router]);

  const onReject = useCallback(() => {
    rejectedStore.add(deliveryId); // 서버 미반영, 로컬에서만 숨김
    router.back();
  }, [deliveryId, router]);

  return (
    <Screen edges={['top', 'bottom']}>
      <Header onBack={goBack} orderId={detail?.orderId} />

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.brand} />
        </View>
      ) : error || !detail ? (
        <View style={styles.centerFill}>
          <AppText variant="medium" style={styles.errorText}>
            {error ?? '요청을 불러오지 못했어요'}
          </AppText>
        </View>
      ) : (
        <>
          <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
            <View style={styles.liveRow}>
              <View style={styles.liveDot} />
              <AppText variant="bold" style={styles.liveText}>
                새 배송 요청 도착
              </AppText>
            </View>

            <AppText variant="display" style={styles.itemName}>
              {detail.furniture.itemName}
            </AppText>
            <View style={styles.categoryPill}>
              <AppText variant="bold" style={styles.categoryText}>
                {detail.furniture.category}
              </AppText>
            </View>

            <View style={styles.feeCard}>
              <AppText variant="medium" style={styles.feeLabel}>
                예상 배송비
              </AppText>
              <AppText variant="display" style={styles.feeValue}>
                {formatFee(detail.deliveryFee)}
              </AppText>
            </View>

            <View style={styles.routeCard}>
              <RouteLine
                pickup={{
                  label: '출발지 · 픽업',
                  place: shortAddress(detail.pickupAddress),
                  address: detail.pickupAddress,
                }}
                destination={{
                  label: '목적지 · 배송',
                  place: shortAddress(detail.destinationAddress),
                  address: detail.destinationAddress,
                }}
              />
            </View>
          </ScrollView>

          <View style={styles.footer}>
            <PrimaryButton label="거절" variant="ghost" onPress={onReject} style={{ flex: 1 }} />
            <PrimaryButton
              label="수락하기"
              onPress={onAccept}
              loading={accepting}
              style={{ flex: 2 }}
            />
          </View>
        </>
      )}
    </Screen>
  );
}

function Header({ onBack, orderId }: { onBack: () => void; orderId?: number }) {
  return (
    <View style={styles.header}>
      <Pressable
        onPress={onBack}
        style={({ pressed }) => [styles.backBtn, pressed && styles.backPressed]}
      >
        <AppText style={styles.backArrow}>‹</AppText>
      </Pressable>
      <View>
        <AppText variant="medium" style={styles.headerLabel}>
          배차 요청
        </AppText>
        <AppText variant="display" style={styles.headerTitle}>
          {orderId ? `주문 #${orderId}` : '상세'}
        </AppText>
      </View>
    </View>
  );
}
