import { useCallback } from 'react';
import { ActivityIndicator, Pressable, ScrollView, View } from 'react-native';
import { useRouter } from 'expo-router';
import { DeliveryStatus } from '@/model/delivery';
import { formatFee, formatTimeKst, shortAddress } from '@/lib/format';
import { AppText } from '@/components/AppText';
import { Screen } from '@/components/Screen';
import { StatusPill } from '@/components/StatusPill';
import { RouteLine } from '@/components/RouteLine';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Toast, useToast } from '@/components/Toast';
import { colors } from '@/theme';
import { useShipmentDetail } from './useShipmentDetail';
import { styles } from './ShipmentDetailScreen.styles';

/** 내 배차 상세 + 상태 변경(수령 → 완료). */
export function ShipmentDetailScreen({ deliveryId }: { deliveryId: number }) {
  const router = useRouter();
  const { detail, loading, working, error, advance } = useShipmentDetail(deliveryId);
  const { message, show } = useToast();

  const onAdvance = useCallback(async () => {
    const res = await advance();
    if (res.message) show(res.message);
  }, [advance, show]);

  const cta = detail ? ctaFor(detail.status) : null;

  return (
    <Screen edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Pressable
          onPress={() => router.back()}
          style={({ pressed }) => [styles.backBtn, pressed && styles.backPressed]}
        >
          <AppText style={styles.backArrow}>‹</AppText>
        </Pressable>
        <View>
          <AppText variant="medium" style={styles.headerLabel}>
            배차 상세
          </AppText>
          <AppText variant="display" style={styles.headerTitle}>
            {detail ? `주문 #${detail.orderId}` : '상세'}
          </AppText>
        </View>
      </View>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.brand} />
        </View>
      ) : error || !detail ? (
        <View style={styles.centerFill}>
          <AppText variant="medium" style={styles.errorText}>
            {error ?? '배차를 불러오지 못했어요'}
          </AppText>
        </View>
      ) : (
        <>
          <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
            <View style={styles.titleRow}>
              <View style={{ flex: 1 }}>
                <View style={styles.categoryPill}>
                  <AppText variant="bold" style={styles.categoryText}>
                    {detail.furniture.category}
                  </AppText>
                </View>
                <AppText variant="display" style={styles.itemName}>
                  {detail.furniture.itemName}
                </AppText>
              </View>
              <StatusPill status={detail.status} />
            </View>

            <Stepper
              status={detail.status}
              acceptedAt={detail.acceptedAt}
              pickedUpAt={detail.pickedUpAt}
              deliveredAt={detail.deliveredAt}
            />

            <View style={styles.feeCard}>
              <AppText variant="medium" style={styles.feeLabel}>
                배송비
              </AppText>
              <AppText variant="display" style={styles.feeValue}>
                {formatFee(detail.deliveryFee)}
              </AppText>
            </View>

            <View style={styles.routeCard}>
              <RouteLine
                pickup={{
                  label: '출발지 · 픽업',
                  place: shortAddress(detail.pickup.address),
                  address: detail.pickup.address,
                  phone: detail.pickup.phoneNumber,
                  phoneLabel: '판매자',
                }}
                destination={{
                  label: '목적지 · 배송',
                  place: shortAddress(detail.destination.address),
                  address: detail.destination.address,
                  phone: detail.destination.phoneNumber,
                  phoneLabel: '구매자',
                }}
              />
            </View>
          </ScrollView>

          {cta ? (
            <View style={styles.footer}>
              <PrimaryButton
                label={cta.label}
                onPress={onAdvance}
                loading={working}
                disabled={cta.disabled}
              />
            </View>
          ) : null}
        </>
      )}

      <Toast message={message} />
    </Screen>
  );
}

function ctaFor(status: DeliveryStatus): { label: string; disabled: boolean } {
  if (status === 'ACCEPTED') return { label: '물건 수령 완료', disabled: false };
  if (status === 'PICKED_UP') return { label: '배송 완료', disabled: false };
  return { label: '배송 완료됨', disabled: true };
}

function Stepper({
  status,
  acceptedAt,
  pickedUpAt,
  deliveredAt,
}: {
  status: DeliveryStatus;
  acceptedAt: string | null;
  pickedUpAt: string | null;
  deliveredAt: string | null;
}) {
  const done2 = status === 'PICKED_UP' || status === 'DELIVERED';
  const done3 = status === 'DELIVERED';
  return (
    <View style={styles.stepper}>
      <Step num="1" label="수락" time={formatTimeKst(acceptedAt)} done />
      <Line done={done2} />
      <Step num="2" label="수령" time={formatTimeKst(pickedUpAt)} done={done2} />
      <Line done={done3} />
      <Step num="3" label="완료" time={formatTimeKst(deliveredAt)} done={done3} />
    </View>
  );
}

function Step({
  num,
  label,
  time,
  done,
}: {
  num: string;
  label: string;
  time: string;
  done: boolean;
}) {
  const fg = done ? colors.white : '#8FB0B4';
  const labelColor = done ? colors.brand : '#8FB0B4';
  return (
    <View style={styles.step}>
      <View style={[styles.stepCircle, { backgroundColor: done ? colors.brand : '#E1F2EF' }]}>
        <AppText variant="display" style={[styles.stepNum, { color: fg }]}>
          {num}
        </AppText>
      </View>
      <AppText variant="bold" style={[styles.stepLabel, { color: labelColor }]}>
        {label}
      </AppText>
      <AppText style={styles.stepTime}>{time}</AppText>
    </View>
  );
}

function Line({ done }: { done: boolean }) {
  return <View style={[styles.stepLine, { backgroundColor: done ? colors.brand : '#E1F2EF' }]} />;
}
