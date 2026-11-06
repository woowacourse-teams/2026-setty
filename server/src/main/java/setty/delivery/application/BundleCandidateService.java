package setty.delivery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.GridCell;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * 배송원이 서 있는 칸에서 출발해 진행 중 배차의 도착 칸(+이웃 8칸)으로 가는 대기 요청을 묶음 후보로 고른다.
 * 배송원 위치는 칸 번호만 이 서버 메모리에 두고 DB와 로그에 남기지 않는다.
 */
@Service
public class BundleCandidateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundleCandidateService.class);

    static final int MAX_CANDIDATES = 5;
    // 웹은 10초마다 위치를 보낸다. 화면을 닫은 배송원의 칸이 남아 알림 대상이 되지 않게 한다.
    static final Duration LOCATION_TTL = Duration.ofMinutes(2);
    private static final Comparator<DeliveryRequest.Summary> HIGHER_FEE_FIRST = Comparator
            .comparingLong(DeliveryRequest.Summary::deliveryFee).reversed()
            .thenComparingLong(DeliveryRequest.Summary::deliveryId);

    private final CellCallCache cellCallCache;
    private final DeliveryQueryRepository deliveryQueryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Map<Long, DriverPosition> positions = new ConcurrentHashMap<>();

    public BundleCandidateService(
            final CellCallCache cellCallCache,
            final DeliveryQueryRepository deliveryQueryRepository,
            final ApplicationEventPublisher eventPublisher,
            final Clock clock
    ) {
        this.cellCallCache = cellCallCache;
        this.deliveryQueryRepository = deliveryQueryRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 위치를 칸으로 바꿔 기억하고, 칸이 바뀌었을 때만 후보를 다시 고른다. */
    public int updateLocation(final DriverId driverId, final double latitude, final double longitude) {
        final int cell = GridCell.locate(latitude, longitude)
                .map(GridCell::code)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
        final Instant now = clock.instant();
        final DriverPosition previous = positions.get(driverId.value());
        positions.compute(driverId.value(), (id, old) -> old != null && old.cell() == cell
                ? new DriverPosition(cell, now, old.sentIds())
                : new DriverPosition(cell, now, old == null ? null : old.sentIds()));
        if (previous == null || previous.cell() != cell) {
            refresh(List.of(driverId.value()));
        }
        return cell;
    }

    public Candidates findCandidates(final DriverId driverId) {
        final DriverPosition position = positions.get(driverId.value());
        if (position == null) {
            return new Candidates(null, List.of());
        }
        final List<DeliveryRequest.Summary> candidates = select(position.cell(),
                deliveryQueryRepository.findInProgressDeliveryCells(List.of(driverId.value()))
                        .getOrDefault(driverId.value(), Set.of()));
        // 웹이 이 결과를 보고 있으므로, 다음 알림은 이 목록과 달라질 때만 보낸다.
        positions.computeIfPresent(driverId.value(), (id, current) ->
                new DriverPosition(current.cell(), current.seenAt(), deliveryIds(candidates)));
        return new Candidates(position.cell(), candidates);
    }

    // 커밋 전에 다시 읽으면 바뀌기 전 행을 담으므로 커밋 후에 한 칸 쌍만 다시 읽는다.
    // 목록 변경 SSE를 받은 웹이 후보를 다시 부를 때 갱신된 캐시를 보도록 그 알림보다 먼저 돈다.
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final DeliveryRequestsChanged event) {
        if (event.pickupCell() == null || event.deliveryCell() == null) {
            return;
        }
        try {
            if (cellCallCache.reload(event.pickupCell(), event.deliveryCell())) {
                refresh(driversIn(Set.of(event.pickupCell())));
            }
        } catch (final RuntimeException exception) {
            LOGGER.warn("칸 캐시 갱신에 실패했습니다. 다음 DB 맞춤에서 다시 반영합니다.", exception);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void fillOnStartup() {
        try {
            cellCallCache.reloadAll();
        } catch (final RuntimeException exception) {
            LOGGER.warn("칸 캐시를 처음 채우지 못했습니다. 다음 DB 맞춤에서 다시 채웁니다.", exception);
        }
    }

    // 무중단 배포 중에는 두 서버가 캐시를 따로 가지므로, 다른 서버가 처리한 수락·취소를 주기적으로 DB에서 맞춘다.
    @Scheduled(
            fixedDelayString = "${setty.delivery.bundle.reconcile-interval-seconds:60}",
            initialDelayString = "${setty.delivery.bundle.reconcile-interval-seconds:60}",
            timeUnit = TimeUnit.SECONDS
    )
    public void reconcile() {
        final Instant expiredBefore = clock.instant().minus(LOCATION_TTL);
        positions.values().removeIf(position -> position.seenAt().isBefore(expiredBefore));
        refresh(driversIn(cellCallCache.reloadAll()));
    }

    private void refresh(final Collection<Long> driverIds) {
        if (driverIds.isEmpty()) {
            return;
        }
        final Map<Long, Set<Integer>> destinations = deliveryQueryRepository.findInProgressDeliveryCells(driverIds);
        for (final Long driverId : driverIds) {
            final DriverPosition position = positions.get(driverId);
            if (position == null) {
                continue;
            }
            final List<Long> candidateIds = deliveryIds(
                    select(position.cell(), destinations.getOrDefault(driverId, Set.of())));
            if (candidateIds.equals(position.sentIds())) {
                continue;
            }
            positions.computeIfPresent(driverId, (id, current) ->
                    new DriverPosition(current.cell(), current.seenAt(), candidateIds));
            eventPublisher.publishEvent(new BundleCandidatesChanged(driverId));
        }
    }

    private List<DeliveryRequest.Summary> select(final int cell, final Set<Integer> destinationCells) {
        final Map<Integer, List<DeliveryRequest.Summary>> callsFromHere = cellCallCache.callsFrom(cell);
        if (destinationCells.isEmpty() || callsFromHere.isEmpty()) {
            return List.of();
        }
        return destinationCells.stream()
                .flatMap(destination -> GridCell.fromCode(destination).withNeighborCodes().stream())
                .distinct()
                .map(callsFromHere::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .sorted(HIGHER_FEE_FIRST)
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private Set<Long> driversIn(final Set<Integer> cells) {
        if (cells.isEmpty()) {
            return Set.of();
        }
        return positions.entrySet().stream()
                .filter(entry -> cells.contains(entry.getValue().cell()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static List<Long> deliveryIds(final List<DeliveryRequest.Summary> requests) {
        return requests.stream().map(DeliveryRequest.Summary::deliveryId).toList();
    }

    public record Candidates(Integer cell, List<DeliveryRequest.Summary> requests) {
    }

    // sentIds는 마지막으로 알린(또는 웹이 조회한) 후보 목록이다. 처음 칸을 받은 배송원은 null이라 한 번은 알린다.
    private record DriverPosition(int cell, Instant seenAt, List<Long> sentIds) {
    }
}
