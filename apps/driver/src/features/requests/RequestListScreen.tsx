import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import { deliveryApi } from '@/api/deliveryApi';
import { DeliveryRequestSummaryResponse } from '@/model/delivery';
import { AppText } from '@/components/AppText';
import { Screen } from '@/components/Screen';
import { Toast, useToast } from '@/components/Toast';
import { colors } from '@/theme';
import { useRequests } from './useRequests';
import { RequestCard } from './RequestCard';
import { styles } from './RequestListScreen.styles';

/** 홈: 수락 대기 중인 배차 요청 목록. */
export function RequestListScreen() {
  const router = useRouter();
  const { items, loading, refreshing, error, refresh, rejectLocal } = useRequests();
  const { message, show } = useToast();
  const [acceptingId, setAcceptingId] = useState<number | null>(null);

  // 상세에서 수락·거절 후 돌아오면 목록을 다시 맞춘다(최초 포커스는 초기 로드가 담당).
  const firstFocus = useRef(true);
  useFocusEffect(
    useCallback(() => {
      if (firstFocus.current) {
        firstFocus.current = false;
        return;
      }
      void refresh();
    }, [refresh]),
  );

  const openDetail = useCallback(
    (deliveryId: number) =>
      router.push({
        pathname: '/request/[deliveryId]',
        params: { deliveryId: String(deliveryId) },
      }),
    [router],
  );

  const acceptInline = useCallback(
    async (deliveryId: number) => {
      if (acceptingId !== null) return;
      setAcceptingId(deliveryId);
      try {
        await deliveryApi.acceptRequest(deliveryId);
        // 수락 성공(응답 바디 없음) → 목록 재조회
        await refresh();
        show('배차 완료! 내 배차에 담았어요');
      } catch {
        show('수락에 실패했어요. 다시 시도해 주세요');
      } finally {
        setAcceptingId(null);
      }
    },
    [acceptingId, refresh, show],
  );

  const renderItem = useCallback(
    ({ item }: { item: DeliveryRequestSummaryResponse }) => (
      <RequestCard
        request={item}
        onPress={() => openDetail(item.deliveryId)}
        onAccept={() => acceptInline(item.deliveryId)}
      />
    ),
    [openDetail, acceptInline],
  );

  return (
    <Screen edges={['top']}>
      <Header count={items.length} refreshing={refreshing} onRefresh={refresh} />

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.brand} />
        </View>
      ) : error ? (
        <View style={styles.centerFill}>
          <AppText variant="medium" style={styles.errorText}>
            {error}
          </AppText>
          <Pressable onPress={refresh} hitSlop={8}>
            <AppText variant="bold" style={styles.retryText}>
              다시 시도
            </AppText>
          </Pressable>
        </View>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(it) => String(it.deliveryId)}
          renderItem={renderItem}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={colors.brand} />
          }
          ListEmptyComponent={<EmptyState />}
        />
      )}

      <Toast message={message} />
    </Screen>
  );
}

function Header({
  count,
  refreshing,
  onRefresh,
}: {
  count: number;
  refreshing: boolean;
  onRefresh: () => void;
}) {
  return (
    <View style={styles.header}>
      <View style={styles.liveRow}>
        <View style={styles.liveDot} />
        <AppText variant="bold" style={styles.liveText}>
          실시간 요청
        </AppText>
      </View>
      <View style={styles.titleRow}>
        <View style={styles.titleLeft}>
          <AppText variant="display" style={styles.title}>
            배차 요청
          </AppText>
          <View style={styles.countPill}>
            <AppText variant="bold" style={styles.countText}>
              수락 대기 {count}건
            </AppText>
          </View>
        </View>
        <Pressable
          onPress={onRefresh}
          disabled={refreshing}
          style={({ pressed }) => [styles.refreshBtn, pressed && styles.refreshPressed]}
        >
          <AppText variant="bold" style={styles.refreshText}>
            새로고침
          </AppText>
        </Pressable>
      </View>
      <AppText variant="medium" style={styles.hint}>
        카드를 탭하면 상세를 볼 수 있어요
      </AppText>
    </View>
  );
}

function EmptyState() {
  return (
    <View style={styles.centerFill}>
      <AppText variant="medium" style={styles.emptyTitle}>
        지금은 대기 중인 요청이 없어요
      </AppText>
      <AppText style={styles.emptySub}>새 요청이 오면 여기에 표시돼요</AppText>
    </View>
  );
}
