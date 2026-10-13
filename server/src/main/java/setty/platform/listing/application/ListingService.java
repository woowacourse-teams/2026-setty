package setty.platform.listing.application;

import static setty.global.exception.ErrorCode.CANNOT_ORDER_OWN_LISTING;
import static setty.global.exception.ErrorCode.LISTING_NOT_FOUND;

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
import setty.global.exception.BusinessException;
import setty.global.logging.ListingRequestTiming;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingImage;
import setty.platform.listing.domain.SaleStatus;
import setty.platform.listing.repository.ListingImageRepository;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.listing.storage.ListingImageStorage;

@Service
public class ListingService {

    private static final Logger log = LoggerFactory.getLogger(ListingService.class);

    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingImageStorage listingImageStorage;
    private final ListingRecorder listingRecorder;

    public ListingService(
            ListingRepository listingRepository,
            ListingImageRepository listingImageRepository,
            ListingImageStorage listingImageStorage,
            ListingRecorder listingRecorder
    ) {
        this.listingRepository = listingRepository;
        this.listingImageRepository = listingImageRepository;
        this.listingImageStorage = listingImageStorage;
        this.listingRecorder = listingRecorder;
    }

    public ListingView.Created create(Long sellerId, ListingCreateCommand command) {
        List<MultipartFile> images = normalizeImages(command.images());
        ListingRecorder.validateImageCount(images.size());
        List<String> objectKeys = listingImageStorage.upload(images);
        try {
            return listingRecorder.create(sellerId, command, objectKeys);
        } catch (RuntimeException exception) {
            safelyDeleteImages(objectKeys, "등록 실패 보상 삭제");
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ListingView.Summary> findAvailableListings() {
        List<Listing> listings = ListingRequestTiming.measure("listings", () -> listingRepository
                .findAllBySaleStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(SaleStatus.AVAILABLE));
        ListingRequestTiming.count("listing_count", listings.size());
        Map<Long, ListingImage> thumbnails = ListingRequestTiming.measure("images", () -> findThumbnails(listings));

        List<ListingView.Summary> summaries = ListingRequestTiming.measure("mapping", () -> listings.stream()
                .map(listing -> toSummary(listing, thumbnails.get(listing.getId())))
                .toList());
        ListingRequestTiming.count("summary_count", summaries.size());
        return summaries;
    }

    @Transactional(readOnly = true)
    public ListingView.Detail findDetail(Long listingId) {
        Listing listing = findActive(listingId);
        List<ListingView.Image> images = listingImageRepository
                .findAllByListingIdOrderByDisplayOrderAsc(listingId)
                .stream()
                .map(this::toImageView)
                .toList();

        return toDetail(listing, images);
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

    @Transactional(readOnly = true)
    public List<ListingView.Summary> findSummaries(List<Long> listingIds) {
        if (listingIds.isEmpty()) {
            return List.of();
        }
        List<Listing> listings = listingRepository.findAllByIdInAndDeletedAtIsNull(listingIds);
        Map<Long, Listing> listingById = listings.stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));
        Map<Long, ListingImage> thumbnails = findThumbnails(listings);

        return listingIds.stream()
                .map(listingById::get)
                .filter(Objects::nonNull)
                .map(listing -> toSummary(listing, thumbnails.get(listing.getId())))
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
    public ListingView.PurchaseInfo registerPurchaseRequest(Long listingId, Long buyerId) {
        Listing listing = findActiveForUpdate(listingId);
        if (listing.isOwnedBy(buyerId)) {
            throw new BusinessException(CANNOT_ORDER_OWN_LISTING);
        }

        listing.registerPurchaseRequest();
        return new ListingView.PurchaseInfo(
                listing.getId(),
                listing.getSellerId(),
                listing.getTitle(),
                listing.getCategory(),
                listing.getPrice(),
                listing.getDeliveryFee(),
                listing.getTotalPrice()
        );
    }

    // 결제 실패로 주문이 취소될 때 선점을 해제한다. 매물이 이미 삭제됐으면 되돌릴 선점도 없으므로 조용히 통과한다.
    @Transactional
    public void releasePurchaseRequest(Long listingId) {
        listingRepository.findActiveByIdForUpdate(listingId)
                .ifPresent(Listing::releasePurchaseRequest);
    }

    @Transactional
    public void releasePurchaseRequestForExpiredPendingOrder(Long listingId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(LISTING_NOT_FOUND));
        listing.releasePurchaseRequestForExpiredPendingOrder();
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

    private ListingView.Summary toSummary(Listing listing, ListingImage thumbnail) {
        return new ListingView.Summary(
                listing.getId(),
                listing.getTitle(),
                thumbnail == null ? null : listingImageStorage.publicUrl(thumbnail.getObjectKey()),
                listing.getPrice(),
                listing.getDeliveryFee(),
                listing.getTotalPrice(),
                listing.getCategory(),
                listing.getConditionGrade(),
                dimensionsOf(listing),
                listing.getCreatedAt()
        );
    }

    private ListingView.Detail toDetail(Listing listing, List<ListingView.Image> images) {
        return new ListingView.Detail(
                listing.getId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getPrice(),
                listing.getDeliveryFee(),
                listing.getTotalPrice(),
                listing.getCategory(),
                listing.getConditionGrade(),
                dimensionsOf(listing),
                listing.getSaleStatus(),
                images,
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
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
                listing.getCreatedAt()
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
}
