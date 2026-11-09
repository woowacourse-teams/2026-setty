package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.application.readmodel.Shipment;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.delivery.DeliveryStatus;
import setty.delivery.domain.GridCell;
import setty.global.exception.BusinessException;

class BundleCandidateServiceTest {

    private static final GridCell HERE = new GridCell(44817, 16641);
    private static final GridCell NEXT_TO_HERE = new GridCell(44818, 16641);
    private static final GridCell MY_DESTINATION = new GridCell(44826, 16672);
    private static final GridCell NEAR_MY_DESTINATION = new GridCell(44827, 16673);
    private static final GridCell FAR_FROM_MY_DESTINATION = new GridCell(44829, 16672);
    private static final DriverId DRIVER = new DriverId(1L);
    private static final DriverId OTHER_DRIVER = new DriverId(2L);

    private final FakeDeliveries deliveries = new FakeDeliveries();
    private final List<Object> events = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private CellCallCache cache;
    private BundleCandidateService service;

    @BeforeEach
    void setUp() {
        cache = new CellCallCache(deliveries);
        service = new BundleCandidateService(cache, deliveries, events::add, clock);
    }

    @Test
    void 지금_칸에서_출발해_내_도착_칸이나_이웃_칸으로_가는_콜만_후보다() {
        deliveries.inProgress(100, DRIVER, MY_DESTINATION);
        deliveries.waiting(1, 10_000, HERE, MY_DESTINATION);
        deliveries.waiting(2, 20_000, HERE, NEAR_MY_DESTINATION);
        deliveries.waiting(3, 30_000, HERE, FAR_FROM_MY_DESTINATION);
        deliveries.waiting(4, 40_000, NEXT_TO_HERE, MY_DESTINATION);
        service.fillOnStartup();

        service.updateLocation(DRIVER, latitudeOf(HERE), longitudeOf(HERE));

        assertThat(ids(service.findCandidates(DRIVER))).containsExactly(2L, 1L);
        assertThat(service.findCandidates(DRIVER).cell()).isEqualTo(HERE.code());
    }

    @Test
    void 배송비_높은_순으로_다섯_건까지만_보낸다() {
        deliveries.inProgress(100, DRIVER, MY_DESTINATION);
        for (int i = 1; i <= 4; i++) {
            deliveries.waiting(i, i * 1_000, HERE, MY_DESTINATION);
            deliveries.waiting(10 + i, i * 1_100, HERE, NEAR_MY_DESTINATION);
        }
        service.fillOnStartup();

        service.updateLocation(DRIVER, latitudeOf(HERE), longitudeOf(HERE));

        // 칸 쌍마다 상위 3건만 담으므로 (4, 3, 2)와 (14, 13, 12)를 합친 뒤 5건을 고른다.
        assertThat(ids(service.findCandidates(DRIVER))).containsExactly(14L, 4L, 13L, 3L, 12L);
    }

    @Test
    void 진행_중_배차가_없거나_위치를_모르면_빈_목록이다() {
        deliveries.waiting(1, 10_000, HERE, MY_DESTINATION);
        service.fillOnStartup();

        assertThat(service.findCandidates(DRIVER).cell()).isNull();
        assertThat(ids(service.findCandidates(DRIVER))).isEmpty();

        service.updateLocation(DRIVER, latitudeOf(HERE), longitudeOf(HERE));
        assertThat(ids(service.findCandidates(DRIVER))).isEmpty();
    }

    @Test
    void 칸이_바뀌고_후보가_달라질_때만_그_배송원에게_알린다() {
        deliveries.inProgress(100, DRIVER, MY_DESTINATION);
        deliveries.waiting(1, 10_000, HERE, MY_DESTINATION);
        service.fillOnStartup();
        service.updateLocation(DRIVER, latitudeOf(NEXT_TO_HERE), longitudeOf(NEXT_TO_HERE));
        events.clear();

        service.updateLocation(DRIVER, latitudeOf(NEXT_TO_HERE) + 0.0001, longitudeOf(NEXT_TO_HERE));
        assertThat(events).isEmpty();

        service.updateLocation(DRIVER, latitudeOf(HERE), longitudeOf(HERE));
        assertThat(events).containsExactly(new BundleCandidatesChanged(DRIVER.value()));
    }

    @Test
    void 새_콜은_그_출발_칸에_있고_도착_조건이_맞는_배송원에게만_알린다() {
        final DriverId farDestinationDriver = new DriverId(3L);
        deliveries.inProgress(100, DRIVER, MY_DESTINATION);
        deliveries.inProgress(101, OTHER_DRIVER, MY_DESTINATION);
        deliveries.inProgress(102, farDestinationDriver, new GridCell(44700, 16500));
        service.fillOnStartup();
        service.updateLocation(DRIVER, latitudeOf(HERE), longitudeOf(HERE));
        service.updateLocation(OTHER_DRIVER, latitudeOf(NEXT_TO_HERE), longitudeOf(NEXT_TO_HERE));
        service.updateLocation(farDestinationDriver, latitudeOf(HERE), longitudeOf(HERE));
        events.clear();

        deliveries.waiting(1, 10_000, HERE, NEAR_MY_DESTINATION);
        service.on(new DeliveryRequestsChanged(HERE.code(), NEAR_MY_DESTINATION.code()));

        assertThat(events).containsExactly(new BundleCandidatesChanged(DRIVER.value()));
        assertThat(ids(service.findCandidates(DRIVER))).containsExactly(1L);
    }

    @Test
    void 수락_취소로_콜이_빠지면_보고_있던_배송원에게_알리고_배차_취소로_돌아오면_다시_넣는다() {
        deliveries.inProgress(100, DRIVER, MY_DESTINATION);
        deliveries.waiting(1, 10_000, HERE, MY_DESTINATION);
        service.fillOnStartup();
        service.updateLocation(DRIVER, latitudeOf(HERE), longitudeOf(HERE));
        events.clear();

        deliveries.take(1);
        service.on(new DeliveryRequestsChanged(HERE.code(), MY_DESTINATION.code()));
        assertThat(events).containsExactly(new BundleCandidatesChanged(DRIVER.value()));
        assertThat(ids(service.findCandidates(DRIVER))).isEmpty();

        deliveries.waiting(1, 10_000, HERE, MY_DESTINATION);
        service.on(new DeliveryRequestsChanged(HERE.code(), MY_DESTINATION.code()));
        assertThat(ids(service.findCandidates(DRIVER))).containsExactly(1L);
    }

    @Test
    void 칸이_없는_요청의_변경은_캐시를_건드리지_않는다() {
        service.on(new DeliveryRequestsChanged(null, null));

        assertThat(deliveries.pairQueries).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    void 다른_서버가_처리한_변경은_주기적인_DB_맞춤에서_반영한다() {
        deliveries.inProgress(100, DRIVER, MY_DESTINATION);
        service.fillOnStartup();
        service.updateLocation(DRIVER, latitudeOf(HERE), longitudeOf(HERE));
        events.clear();

        deliveries.waiting(1, 10_000, HERE, MY_DESTINATION);
        assertThat(ids(service.findCandidates(DRIVER))).isEmpty();

        service.reconcile();

        assertThat(events).containsExactly(new BundleCandidatesChanged(DRIVER.value()));
        assertThat(ids(service.findCandidates(DRIVER))).containsExactly(1L);
    }

    @Test
    void 위치가_끊긴_배송원은_DB_맞춤_때_지운다() {
        deliveries.inProgress(100, DRIVER, MY_DESTINATION);
        service.updateLocation(DRIVER, latitudeOf(HERE), longitudeOf(HERE));

        clock.advance(BundleCandidateService.LOCATION_TTL.plusSeconds(1));
        service.reconcile();

        assertThat(service.findCandidates(DRIVER).cell()).isNull();
    }

    @Test
    void 격자_밖_좌표는_거절한다() {
        assertThatThrownBy(() -> service.updateLocation(DRIVER, Double.NaN, 126.9))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateLocation(DRIVER, -33.8, 151.2))
                .isInstanceOf(BusinessException.class);
    }

    private static List<Long> ids(final BundleCandidateService.Candidates candidates) {
        return candidates.requests().stream().map(DeliveryRequest.Summary::deliveryId).toList();
    }

    private static double latitudeOf(final GridCell cell) {
        return (cell.row() + 0.5) * 0.25 / 110.95;
    }

    private static double longitudeOf(final GridCell cell) {
        return (cell.column() + 0.5) * 0.25 / (111.32 * Math.cos(Math.toRadians(37.55)));
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-06T00:00:00Z");

        void advance(final Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakeDeliveries implements DeliveryQueryRepository {

        private final Map<Long, Row> rows = new HashMap<>();
        private int pairQueries;

        void waiting(final long id, final long fee, final GridCell pickup, final GridCell destination) {
            rows.put(id, new Row(id, fee, null, pickup.code(), destination.code()));
        }

        void inProgress(final long id, final DriverId driverId, final GridCell destination) {
            rows.put(id, new Row(id, 0, driverId.value(), HERE.code(), destination.code()));
        }

        void take(final long id) {
            final Row row = rows.get(id);
            rows.put(id, new Row(id, row.fee(), 999L, row.pickupCell(), row.deliveryCell()));
        }

        @Override
        public List<DeliveryRequest.CellCall> findCellCalls() {
            return waitingRows()
                    .sorted(java.util.Comparator.comparingInt(Row::pickupCell)
                            .thenComparingInt(Row::deliveryCell)
                            .thenComparing(java.util.Comparator.comparingLong(Row::fee).reversed())
                            .thenComparingLong(Row::id))
                    .map(row -> new DeliveryRequest.CellCall(row.pickupCell(), row.deliveryCell(), summary(row)))
                    .toList();
        }

        @Override
        public List<DeliveryRequest.Summary> findCellCalls(final int pickupCell, final int deliveryCell,
                                                           final int limit) {
            pairQueries++;
            return findCellCalls().stream()
                    .filter(call -> call.pickupCell() == pickupCell && call.deliveryCell() == deliveryCell)
                    .limit(limit)
                    .map(DeliveryRequest.CellCall::summary)
                    .toList();
        }

        @Override
        public Map<Long, Set<Integer>> findInProgressDeliveryCells(final Collection<Long> driverIds) {
            final Map<Long, Set<Integer>> cells = new HashMap<>();
            rows.values().stream()
                    .filter(row -> row.driverId() != null && driverIds.contains(row.driverId()))
                    .forEach(row -> cells.computeIfAbsent(row.driverId(), id -> new HashSet<>()).add(row.deliveryCell()));
            return cells;
        }

        private java.util.stream.Stream<Row> waitingRows() {
            return rows.values().stream().filter(row -> row.driverId() == null);
        }

        private static DeliveryRequest.Summary summary(final Row row) {
            return new DeliveryRequest.Summary(row.id(), row.id(), "가상 의자", "CHAIR", "출발", "도착", row.fee(),
                    DeliveryStatus.REQUESTED, Instant.parse("2026-11-06T00:00:00Z"), null, null);
        }

        @Override
        public List<DeliveryRequest.Summary> findAvailableRequests() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DeliveryRequest.Summary> findAvailableRequests(final DeliveryCursor cursor, final Integer limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DeliveryRequest.Detail> findAvailableRequestById(final DeliveryId deliveryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Shipment.Summary> findShipmentsByDriverId(final DriverId driverId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Shipment.Summary> findShipmentsByDriverId(final DriverId driverId, final ShipmentGroup group,
                                                            final DeliveryCursor cursor, final Integer limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Shipment.Detail> findShipmentByIdAndDriverId(final DeliveryId deliveryId,
                                                                    final DriverId driverId) {
            throw new UnsupportedOperationException();
        }

        private record Row(long id, long fee, Long driverId, int pickupCell, int deliveryCell) {
        }
    }
}
