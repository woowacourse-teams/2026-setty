package setty.delivery.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import setty.delivery.application.readmodel.DeliveryRequest;

/**
 * 출발 칸 → 도착 칸 → 배송비 높은 순 대기 요청 상위 3건. 콜이 있는 칸 쌍만 담는다.
 * 안쪽 맵과 목록은 바꿀 때마다 새로 만들어, 칸을 밟을 때 읽는 쪽은 잠그지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CellCallCache {

    static final int CALLS_PER_PAIR = 3;

    private final DeliveryQueryRepository deliveryQueryRepository;
    private final Object writeLock = new Object();
    private volatile Map<Integer, Map<Integer, List<DeliveryRequest.Summary>>> calls = new ConcurrentHashMap<>();

    public Map<Integer, List<DeliveryRequest.Summary>> callsFrom(final int pickupCell) {
        return calls.getOrDefault(pickupCell, Map.of());
    }

    /** 한 칸 쌍을 DB에서 다시 읽는다. 목록이 바뀌었으면 true다. */
    public boolean reload(final int pickupCell, final int deliveryCell) {
        // 읽기와 바꿔 넣기 사이에 전체 다시 읽기가 끼면 오래된 목록이 덮어쓸 수 있어 쓰기는 한 번에 하나만 한다.
        synchronized (writeLock) {
            final List<DeliveryRequest.Summary> fresh = List.copyOf(
                    deliveryQueryRepository.findCellCalls(pickupCell, deliveryCell, CALLS_PER_PAIR));
            final Map<Integer, List<DeliveryRequest.Summary>> current = callsFrom(pickupCell);
            if (fresh.equals(current.getOrDefault(deliveryCell, List.of()))) {
                return false;
            }
            final Map<Integer, List<DeliveryRequest.Summary>> next = new HashMap<>(current);
            if (fresh.isEmpty()) {
                next.remove(deliveryCell);
            } else {
                next.put(deliveryCell, fresh);
            }
            if (next.isEmpty()) {
                calls.remove(pickupCell);
            } else {
                calls.put(pickupCell, Map.copyOf(next));
            }
            return true;
        }
    }

    /** 전체를 DB에서 다시 만들고, 목록이 달라진 출발 칸을 돌려준다. */
    public Set<Integer> reloadAll() {
        synchronized (writeLock) {
            final Map<Integer, Map<Integer, List<DeliveryRequest.Summary>>> fresh = build(
                    deliveryQueryRepository.findCellCalls());
            final Set<Integer> changed = new HashSet<>();
            final Set<Integer> pickupCells = new HashSet<>(calls.keySet());
            pickupCells.addAll(fresh.keySet());
            for (final Integer pickupCell : pickupCells) {
                if (!Objects.equals(calls.get(pickupCell), fresh.get(pickupCell))) {
                    changed.add(pickupCell);
                }
            }
            calls = fresh;
            return changed;
        }
    }

    int pairCount() {
        return calls.values().stream().mapToInt(Map::size).sum();
    }

    private static Map<Integer, Map<Integer, List<DeliveryRequest.Summary>>> build(
            final List<DeliveryRequest.CellCall> rows
    ) {
        final Map<Integer, Map<Integer, List<DeliveryRequest.Summary>>> building = new HashMap<>();
        for (final DeliveryRequest.CellCall row : rows) {
            final List<DeliveryRequest.Summary> pair = building
                    .computeIfAbsent(row.pickupCell(), cell -> new HashMap<>())
                    .computeIfAbsent(row.deliveryCell(), cell -> new ArrayList<>(CALLS_PER_PAIR));
            // 행이 배송비 높은 순으로 오므로 앞의 3건만 남긴다.
            if (pair.size() < CALLS_PER_PAIR) {
                pair.add(row.summary());
            }
        }
        final Map<Integer, Map<Integer, List<DeliveryRequest.Summary>>> result = new ConcurrentHashMap<>();
        building.forEach((pickupCell, pairs) -> {
            final Map<Integer, List<DeliveryRequest.Summary>> frozen = new HashMap<>();
            pairs.forEach((deliveryCell, list) -> frozen.put(deliveryCell, List.copyOf(list)));
            result.put(pickupCell, Map.copyOf(frozen));
        });
        return result;
    }
}
