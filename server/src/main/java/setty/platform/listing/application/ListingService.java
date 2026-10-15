package setty.platform.listing.application;

import static setty.global.exception.ErrorCode.CANNOT_ORDER_OWN_LISTING;
import static setty.global.exception.ErrorCode.DELIVERY_AREA_NOT_SUPPORTED;
import static setty.global.exception.ErrorCode.INVALID_REQUEST;
import static setty.global.exception.ErrorCode.INVALID_TOKEN;
import static setty.global.exception.ErrorCode.LISTING_NOT_FOUND;
import static setty.global.exception.ErrorCode.MEMBER_DONG_REQUIRED;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import setty.deliveryfee.DeliveryFeeService;
import setty.deliveryfee.Dong;
import setty.deliveryfee.VolumeTier;
import setty.global.exception.BusinessException;
import setty.global.logging.ListingRequestTiming;
import setty.platform.listing.domain.DeliveryFeePolicy;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingImage;
import setty.platform.listing.domain.SaleStatus;
import setty.platform.listing.repository.ListingImageRepository;
import setty.platform.listing.repository.ListingQueryRepository;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.order.domain.OrderStatus;
import setty.platform.order.repository.OrderRepository;

@Service
public class ListingService {

    private static final Logger log = LoggerFactory.getLogger(ListingService.class);
    public static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAXIMUM_PAGE_SIZE = 100;

    private static final List<OrderStatus> CLOSED_ORDER_STATUSES = List.of(OrderStatus.EXPIRED, OrderStatus.CANCELLED);

    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingQueryRepository listingQueryRepository;
    private final ListingImageStorage listingImageStorage;
    private final ListingRecorder listingRecorder;
    private final OrderRepository orderRepository;
    private final DeliveryFeeService deliveryFeeService;

    public ListingService(
            ListingRepository listingRepository,
            ListingImageRepository listingImageRepository,
            ListingQueryRepository listingQueryRepository,
            ListingImageStorage listingImageStorage,
            ListingRecorder listingRecorder,
            OrderRepository orderRepository,
            DeliveryFeeService deliveryFeeService
    ) {
        this.listingRepository = listingRepository;
        this.listingImageRepository = listingImageRepository;
        this.listingQueryRepository = listingQueryRepository;
        this.listingImageStorage = listingImageStorage;
        this.listingRecorder = listingRecorder;
        this.orderRepository = orderRepository;
        this.deliveryFeeService = deliveryFeeService;
    }

    public ListingView.Created create(Long sellerId, String sellerDongCode, ListingCreateCommand command) {
        if (sellerDongCode == null) {
            throw new BusinessException(MEMBER_DONG_REQUIRED);
        }
        List<MultipartFile> images = normalizeImages(command.images());
        ListingRecorder.validateImageCount(images.size());
        List<String> objectKeys = listingImageStorage.upload(images);
        try {
            return listingRecorder.create(sellerId, sellerDongCode, command, objectKeys);
        } catch (RuntimeException exception) {
            safelyDeleteImages(objectKeys, "등록 실패 보상 삭제");
            throw exception;
        }
    }

    // 트랜잭션을 걸지 않는다. JPA 트랜잭션을 열면 조회 내내 커넥션을 붙잡으므로 쿼리마다 바로 반납하는 JDBC 조회만 쓴다.
    public List<ListingView.Summary> findAvailableListings(String buyerToken) {
        return listingQueryRepository.inOneConnection(reader ->
                findAvailable(reader, null, null, null, buyerOf(reader, buyerToken).dong()).items());
    }

    public ListingView.SummaryPage findAvailableListings(
            ListingCursor cursor,
            int size,
            String buyerToken,
            Integer maxDeliveryFee
    ) {
        if (size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new BusinessException(INVALID_REQUEST);
        }
        return listingQueryRepository.inOneConnection(reader -> {
            Buyer buyer = buyerOf(reader, buyerToken);
            Map<VolumeTier, List<String>> sellerDongCodes = maxDeliveryFee == null
                    ? null
                    : sellerDongCodesWithin(buyer, maxDeliveryFee);
            return findAvailable(reader, cursor, size, sellerDongCodes, buyer.dong());
        });
    }

    // 매물 조회는 비로그인도 허용해 인터셉터가 회원을 읽지 않는다. JPA로 회원을 읽으면 OSIV 아래에서
    // 응답이 끝날 때까지 커넥션을 붙잡으므로, 토큰으로 행정동만 JDBC로 한 번 읽는다.
    private Buyer buyerOf(ListingQueryRepository reader, String token) {
        if (token == null) {
            return Buyer.ANONYMOUS;
        }
        return ListingRequestTiming.measure("buyer", () -> reader.findMemberDongByToken(token))
                .map(member -> new Buyer(true, member.dongCode(),
                        member.dongCode() == null ? null : deliveryFeeService.findDong(member.dongCode()).orElse(null)))
                .orElse(Buyer.ANONYMOUS);
    }

    private Map<VolumeTier, List<String>> sellerDongCodesWithin(Buyer buyer, int maxDeliveryFee) {
        if (!buyer.member()) {
            throw new BusinessException(INVALID_TOKEN);
        }
        if (buyer.dongCode() == null) {
            throw new BusinessException(MEMBER_DONG_REQUIRED);
        }
        if (buyer.dong() == null) {
            throw new BusinessException(DELIVERY_AREA_NOT_SUPPORTED);
        }
        Map<VolumeTier, List<String>> sellerDongCodes = new LinkedHashMap<>();
        deliveryFeeService.sellerDongsWithinFee(buyer.dong(), maxDeliveryFee).forEach((volumeTier, dongs) ->
                sellerDongCodes.put(volumeTier, dongs.stream().map(Dong::code).toList()));
        return sellerDongCodes;
    }

    private ListingView.SummaryPage findAvailable(
            ListingQueryRepository reader,
            ListingCursor cursor,
            Integer size,
            Map<VolumeTier, List<String>> sellerDongCodes,
            Dong buyerDong
    ) {
        // 예산 안에 드는 판매자 동이 하나도 없으면 DB에 묻지 않는다
        if (sellerDongCodes != null && sellerDongCodes.isEmpty()) {
            return new ListingView.SummaryPage(List.of(), null);
        }
        // 다음 페이지가 있는지 알려고 한 개를 더 읽는다
        List<ListingQueryRepository.Row> rows = ListingRequestTiming.measure("listings", () -> reader.findAvailable(
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.id(),
                size == null ? null : size + 1,
                sellerDongCodes
        ));
        boolean hasNext = size != null && rows.size() > size;
        List<ListingQueryRepository.Row> page = hasNext ? rows.subList(0, size) : rows;
        ListingRequestTiming.count("listing_count", page.size());

        List<Long> listingIds = page.stream().map(ListingQueryRepository.Row::id).toList();
        Map<Long, String> thumbnails = ListingRequestTiming.measure("images", () -> listingIds.isEmpty()
                ? Map.<Long, String>of()
                : reader.findThumbnailObjectKeys(listingIds));
        ListingRequestTiming.count("image_count", thumbnails.size());

        List<ListingView.Summary> summaries = ListingRequestTiming.measure("mapping", () -> page.stream()
                .map(row -> toSummary(row, thumbnails.get(row.id()), buyerDong))
                .toList());
        ListingRequestTiming.count("summary_count", summaries.size());

        ListingQueryRepository.Row last = hasNext ? page.get(page.size() - 1) : null;
        String nextCursor = last == null ? null : new ListingCursor(last.createdAt(), last.id()).encode();
        return new ListingView.SummaryPage(summaries, nextCursor);
    }

    @Transactional(readOnly = true)
    public ListingView.Detail findDetail(Long listingId, String buyerToken) {
        Listing listing = findActive(listingId);
        List<ListingView.Image> images = listingImageRepository
                .findAllByListingIdOrderByDisplayOrderAsc(listingId)
                .stream()
                .map(this::toImageView)
                .toList();

        return toDetail(listing, images, buyerOf(listingQueryRepository, buyerToken).dong());
    }

    @Transactional(readOnly = true)
    public List<ListingView.Mine> findMine(Long sellerId) {
        List<Listing> listings = listingRepository
                .findAllBySellerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(sellerId);
        Map<Long, ListingImage> thumbnails = findThumbnails(listings);

        return listings.stream()
                .map(listing -> toMine(listing, thumbnails.get(listing.getId())))
                .toList();
    }

    // buyerDongCode가 null이면 등록 때 정한 배송비를 보여 준다. 내 주문은 주문에 저장한 금액을 쓰고, 저장값이 없는 예전 주문만 이 금액을 쓴다.
    @Transactional(readOnly = true)
    public List<ListingView.Summary> findSummaries(List<Long> listingIds, String buyerDongCode) {
        if (listingIds.isEmpty()) {
            return List.of();
        }
        Dong buyerDong = buyerDongCode == null ? null : deliveryFeeService.findDong(buyerDongCode).orElse(null);
        List<Listing> listings = listingRepository.findAllByIdInAndDeletedAtIsNull(listingIds);
        Map<Long, Listing> listingById = listings.stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));
        Map<Long, ListingImage> thumbnails = findThumbnails(listings);

        return listingIds.stream()
                .map(listingById::get)
                .filter(Objects::nonNull)
                .map(listing -> toSummary(listing, thumbnails.get(listing.getId()), buyerDong))
                .toList();
    }

    public void update(Long sellerId, Long listingId, ListingUpdateCommand command) {
        List<MultipartFile> newImages = normalizeImages(command.newImages());
        listingRecorder.validateUpdatable(sellerId, listingId, command, newImages);
        List<String> newObjectKeys = newImages.isEmpty() ? List.of() : listingImageStorage.upload(newImages);
        List<String> removedObjectKeys;
        try {
            removedObjectKeys = listingRecorder.update(sellerId, listingId, command, newObjectKeys);
        } catch (RuntimeException exception) {
            safelyDeleteImages(newObjectKeys, "수정 실패 보상 삭제");
            throw exception;
        }
        safelyDeleteImages(removedObjectKeys, "수정 후 정리");
    }

    @Transactional
    public void delete(Long sellerId, Long listingId) {
        Listing listing = findOwnedActiveForUpdate(sellerId, listingId);
        List<String> objectKeys = listingImageRepository
                .findAllByListingIdOrderByDisplayOrderAsc(listingId)
                .stream()
                .map(ListingImage::getObjectKey)
                .toList();

        listing.softDelete();
        deleteImagesAfterCommit(objectKeys);
    }

    @Transactional
    public ListingView.PurchaseInfo registerPurchaseRequest(Long listingId, Long buyerId, String buyerDongCode) {
        Listing listing = findActiveForUpdate(listingId);
        if (listing.isOwnedBy(buyerId)) {
            throw new BusinessException(CANNOT_ORDER_OWN_LISTING);
        }

        listing.registerPurchaseRequest();
        Dong buyerDong = buyerDongCode == null ? null : deliveryFeeService.findDong(buyerDongCode).orElse(null);
        int deliveryFee = deliveryFeeFor(listing, buyerDong);
        return new ListingView.PurchaseInfo(
                listing.getId(),
                listing.getSellerId(),
                listing.getTitle(),
                listing.getCategory(),
                listing.getPrice(),
                deliveryFee,
                listing.getPrice() + deliveryFee
        );
    }

    // 결제 실패로 주문이 취소될 때 선점을 해제한다. 매물이 이미 삭제됐으면 되돌릴 선점도 없으므로 조용히 통과한다.
    @Transactional
    public void releasePurchaseRequest(Long listingId) {
        listingRepository.findActiveByIdForUpdate(listingId)
                .filter(listing -> !hasActiveOrder(listingId))
                .ifPresent(Listing::releasePurchaseRequest);
    }

    private boolean hasActiveOrder(Long listingId) {
        return orderRepository.existsByListingIdAndOrderStatusNotIn(listingId, CLOSED_ORDER_STATUSES);
    }

    @Transactional
    public void releasePurchaseRequestForExpiredPendingOrder(Long listingId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(LISTING_NOT_FOUND));
        listing.releasePurchaseRequestForExpiredPendingOrder();
    }

    @Transactional
    public void releasePurchaseRequestsForExpiredPendingOrders(List<Long> listingIds) {
        if (listingIds.isEmpty()) {
            return;
        }
        List<Long> distinctIds = listingIds.stream().distinct().sorted().toList();
        // 단건 경로와 같이 삭제 여부와 무관하게 선점만 해제한다. 매물이 누락되면 주문 변경도 롤백한다.
        if (listingRepository.findAllByIdForUpdate(distinctIds).size() != distinctIds.size()) {
            throw new BusinessException(LISTING_NOT_FOUND);
        }
        listingRepository.releasePurchaseRequests(distinctIds, Instant.now());
    }

    @Transactional
    public boolean reserveForDelivery(Long listingId) {
        return findActiveForUpdate(listingId).reserve();
    }

    @Transactional
    public void completeSale(Long listingId) {
        findActiveForUpdate(listingId).completeSale();
    }

    private Listing findOwnedActiveForUpdate(Long sellerId, Long listingId) {
        Listing listing = findActiveForUpdate(listingId);
        if (!listing.isOwnedBy(sellerId)) {
            throw new BusinessException(LISTING_NOT_FOUND);
        }
        return listing;
    }

    private Listing findActive(Long listingId) {
        return listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .orElseThrow(() -> new BusinessException(LISTING_NOT_FOUND));
    }

    private Listing findActiveForUpdate(Long listingId) {
        return listingRepository.findActiveByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(LISTING_NOT_FOUND));
    }

    private Map<Long, ListingImage> findThumbnails(List<Listing> listings) {
        if (listings.isEmpty()) {
            ListingRequestTiming.count("image_count", 0);
            return Map.of();
        }

        List<Long> listingIds = listings.stream().map(Listing::getId).toList();
        Map<Long, ListingImage> thumbnails = new LinkedHashMap<>();
        List<ListingImage> images = listingImageRepository
                .findThumbnailsByListingIdIn(listingIds);
        ListingRequestTiming.count("image_count", images.size());
        images.forEach(image -> thumbnails.putIfAbsent(image.getListingId(), image));
        return thumbnails;
    }

    private List<MultipartFile> normalizeImages(List<MultipartFile> images) {
        return images == null ? List.of() : List.copyOf(images);
    }

    private ListingView.Summary toSummary(ListingQueryRepository.Row row, String thumbnailObjectKey, Dong buyerDong) {
        int deliveryFee = deliveryFeeFor(row.dongCode(), row.volumeTier(), row.deliveryFee(), buyerDong);
        return new ListingView.Summary(
                row.id(),
                row.title(),
                thumbnailObjectKey == null ? null : listingImageStorage.publicUrl(thumbnailObjectKey),
                row.price(),
                deliveryFee,
                row.price() + deliveryFee,
                row.category(),
                row.conditionGrade(),
                new ListingView.Dimensions(row.widthCm(), row.depthCm(), row.heightCm()),
                row.hasPurchaseRequest(),
                row.createdAt(),
                dongOf(row.dongCode())
        );
    }

    private ListingView.Summary toSummary(Listing listing, ListingImage thumbnail, Dong buyerDong) {
        int deliveryFee = deliveryFeeFor(listing, buyerDong);
        return new ListingView.Summary(
                listing.getId(),
                listing.getTitle(),
                thumbnail == null ? null : listingImageStorage.publicUrl(thumbnail.getObjectKey()),
                listing.getPrice(),
                deliveryFee,
                listing.getPrice() + deliveryFee,
                listing.getCategory(),
                listing.getConditionGrade(),
                dimensionsOf(listing),
                listing.hasPurchaseRequest(),
                listing.getCreatedAt(),
                dongOf(listing.getDongCode())
        );
    }

    private ListingView.Detail toDetail(Listing listing, List<ListingView.Image> images, Dong buyerDong) {
        int deliveryFee = deliveryFeeFor(listing, buyerDong);
        return new ListingView.Detail(
                listing.getId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getPrice(),
                deliveryFee,
                listing.getPrice() + deliveryFee,
                listing.getCategory(),
                listing.getConditionGrade(),
                dimensionsOf(listing),
                listing.getSaleStatus(),
                listing.hasPurchaseRequest(),
                images,
                listing.getCreatedAt(),
                listing.getUpdatedAt(),
                dongOf(listing.getDongCode())
        );
    }

    private int deliveryFeeFor(Listing listing, Dong buyerDong) {
        return deliveryFeeFor(listing.getDongCode(), DeliveryFeePolicy.volumeTier(listing.getDimensions()),
                listing.getDeliveryFee(), buyerDong);
    }

    // 구매자나 매물의 행정동을 모르면 거리를 잴 수 없어 등록 때 정한 부피 배송비를 그대로 쓴다
    private int deliveryFeeFor(String sellerDongCode, VolumeTier volumeTier, int fixedFee, Dong buyerDong) {
        if (buyerDong == null || sellerDongCode == null) {
            return fixedFee;
        }
        return deliveryFeeService.findDong(sellerDongCode)
                .map(seller -> deliveryFeeService.fee(seller.index(), buyerDong.index(), volumeTier))
                .orElse(fixedFee);
    }

    // 행정동 도입 전에 등록된 매물은 dong_code가 없다
    private ListingView.Dong dongOf(String dongCode) {
        if (dongCode == null) {
            return null;
        }
        return deliveryFeeService.findDong(dongCode)
                .map(dong -> new ListingView.Dong(dong.code(), dong.name(), dong.district()))
                .orElse(null);
    }

    private ListingView.Mine toMine(Listing listing, ListingImage thumbnail) {
        return new ListingView.Mine(
                listing.getId(),
                listing.getTitle(),
                thumbnail == null ? null : listingImageStorage.publicUrl(thumbnail.getObjectKey()),
                listing.getPrice(),
                listing.getDeliveryFee(),
                listing.getTotalPrice(),
                listing.getCategory(),
                listing.getConditionGrade(),
                dimensionsOf(listing),
                listing.getSaleStatus(),
                listing.hasPurchaseRequest(),
                listing.canUpdate(),
                listing.canDelete(),
                listing.getCreatedAt(),
                dongOf(listing.getDongCode())
        );
    }

    private ListingView.Dimensions dimensionsOf(Listing listing) {
        Dimensions dimensions = listing.getDimensions();
        return new ListingView.Dimensions(
                dimensions.getWidthCm(),
                dimensions.getDepthCm(),
                dimensions.getHeightCm()
        );
    }

    private ListingView.Image toImageView(ListingImage image) {
        return new ListingView.Image(
                image.getId(),
                listingImageStorage.publicUrl(image.getObjectKey()),
                image.getDisplayOrder()
        );
    }

    private void deleteImagesAfterCommit(Collection<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safelyDeleteImages(objectKeys, "커밋 후 정리");
            }
        });
    }

    private void safelyDeleteImages(Collection<String> objectKeys, String operation) {
        try {
            listingImageStorage.deleteAll(objectKeys);
        } catch (RuntimeException exception) {
            log.error("매물 이미지 {} 실패. imageCount={}", operation, objectKeys.size(), exception);
        }
    }

    private record Buyer(boolean member, String dongCode, Dong dong) {

        static final Buyer ANONYMOUS = new Buyer(false, null, null);
    }
}
