package setty.platform.listing.application;

import static setty.global.exception.ErrorCode.CANNOT_ORDER_OWN_LISTING;
import static setty.global.exception.ErrorCode.INVALID_LISTING_IMAGE_COUNT;
import static setty.global.exception.ErrorCode.INVALID_LISTING_IMAGE_REFERENCE;
import static setty.global.exception.ErrorCode.LISTING_NOT_FOUND;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final int MINIMUM_IMAGE_COUNT = 1;
    private static final int MAXIMUM_IMAGE_COUNT = 5;

    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingImageStorage listingImageStorage;

    public ListingService(
            ListingRepository listingRepository,
            ListingImageRepository listingImageRepository,
            ListingImageStorage listingImageStorage
    ) {
        this.listingRepository = listingRepository;
        this.listingImageRepository = listingImageRepository;
        this.listingImageStorage = listingImageStorage;
    }

    @Transactional
    public ListingView.Created create(Long sellerId, ListingCreateCommand command) {
        List<MultipartFile> images = normalizeImages(command.images());
        validateFinalImageCount(images.size());
        List<String> objectKeys = listingImageStorage.upload(images);
        deleteImagesOnRollback(objectKeys);

        Listing listing = listingRepository.save(Listing.create(
                sellerId,
                command.title(),
                command.description(),
                command.price(),
                command.category(),
                command.conditionGrade(),
                command.dimensions()
        ));
        listingRepository.flush();
        listingImageRepository.saveAll(createImages(listing.getId(), objectKeys, 1));

        return new ListingView.Created(listing.getId(), listing.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ListingView.Summary> findAvailableListings() {
        List<Listing> listings = listingRepository
                .findAllBySaleStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(SaleStatus.AVAILABLE);
        Map<Long, ListingImage> thumbnails = findThumbnails(listings);

        return listings.stream()
                .map(listing -> toSummary(listing, thumbnails.get(listing.getId())))
                .toList();
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

    @Transactional
    public void update(Long sellerId, Long listingId, ListingUpdateCommand command) {
        Listing listing = findOwnedActiveForUpdate(sellerId, listingId);
        List<ListingImage> currentImages = listingImageRepository
                .findAllByListingIdOrderByDisplayOrderAsc(listingId);
        List<ListingImage> retainedImages = resolveRetainedImages(currentImages, command.retainedImageIds());
        List<MultipartFile> newImages = normalizeImages(command.newImages());
        validateFinalImageCount(retainedImages.size() + newImages.size());

        List<String> newObjectKeys = newImages.isEmpty()
                ? List.of()
                : listingImageStorage.upload(newImages);
        deleteImagesOnRollback(newObjectKeys);

        listing.update(
                command.title(),
                command.description(),
                command.price(),
                command.category(),
                command.conditionGrade(),
                command.dimensions()
        );

        List<Long> retainedImageIds = retainedImages.stream().map(ListingImage::getId).toList();
        Set<Long> retainedIdSet = new HashSet<>(retainedImageIds);
        List<ListingImage> removedImages = currentImages.stream()
                .filter(image -> !retainedIdSet.contains(image.getId()))
                .toList();
        List<String> removedObjectKeys = removedImages.stream()
                .map(ListingImage::getObjectKey)
                .toList();

        listingImageRepository.shiftDisplayOrders(listingId, MAXIMUM_IMAGE_COUNT);
        listingImageRepository.deleteAllByIdInBatch(removedImages.stream().map(ListingImage::getId).toList());

        List<ListingImage> persistedRetainedImages = findImagesByIdsInOrder(retainedImageIds);
        for (int index = 0; index < persistedRetainedImages.size(); index++) {
            persistedRetainedImages.get(index).changeDisplayOrder(index + 1);
        }
        listingImageRepository.saveAll(persistedRetainedImages);
        listingImageRepository.saveAll(createImages(listingId, newObjectKeys, persistedRetainedImages.size() + 1));
        deleteImagesAfterCommit(removedObjectKeys);
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

    private List<ListingImage> resolveRetainedImages(
            List<ListingImage> currentImages,
            List<Long> retainedImageIds
    ) {
        if (retainedImageIds == null || new HashSet<>(retainedImageIds).size() != retainedImageIds.size()) {
            throw new BusinessException(INVALID_LISTING_IMAGE_REFERENCE);
        }

        Map<Long, ListingImage> currentById = currentImages.stream()
                .collect(Collectors.toMap(ListingImage::getId, Function.identity()));
        List<ListingImage> retained = new ArrayList<>(retainedImageIds.size());
        for (Long retainedImageId : retainedImageIds) {
            ListingImage image = currentById.get(retainedImageId);
            if (image == null) {
                throw new BusinessException(INVALID_LISTING_IMAGE_REFERENCE);
            }
            retained.add(image);
        }
        return retained;
    }

    private List<ListingImage> findImagesByIdsInOrder(List<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ListingImage> imagesById = listingImageRepository.findAllById(imageIds).stream()
                .collect(Collectors.toMap(ListingImage::getId, Function.identity()));
        List<ListingImage> images = new ArrayList<>(imageIds.size());
        for (Long imageId : imageIds) {
            ListingImage image = imagesById.get(imageId);
            if (image == null) {
                throw new BusinessException(INVALID_LISTING_IMAGE_REFERENCE);
            }
            images.add(image);
        }
        return images;
    }

    private Map<Long, ListingImage> findThumbnails(List<Listing> listings) {
        if (listings.isEmpty()) {
            return Map.of();
        }

        List<Long> listingIds = listings.stream().map(Listing::getId).toList();
        Map<Long, ListingImage> thumbnails = new LinkedHashMap<>();
        listingImageRepository
                .findAllByListingIdInOrderByListingIdAscDisplayOrderAsc(listingIds)
                .forEach(image -> thumbnails.putIfAbsent(image.getListingId(), image));
        return thumbnails;
    }

    private List<ListingImage> createImages(Long listingId, List<String> objectKeys, int firstOrder) {
        List<ListingImage> images = new ArrayList<>(objectKeys.size());
        for (int index = 0; index < objectKeys.size(); index++) {
            images.add(ListingImage.create(listingId, objectKeys.get(index), firstOrder + index));
        }
        return images;
    }

    private void validateFinalImageCount(int imageCount) {
        if (imageCount < MINIMUM_IMAGE_COUNT || imageCount > MAXIMUM_IMAGE_COUNT) {
            throw new BusinessException(INVALID_LISTING_IMAGE_COUNT);
        }
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

    private void deleteImagesOnRollback(Collection<String> objectKeys) {
        if (objectKeys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    safelyDeleteImages(objectKeys, "롤백 보상 삭제");
                }
            }
        });
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
