package setty.platform.listing.application;

import static setty.global.exception.ErrorCode.INVALID_LISTING_IMAGE_COUNT;
import static setty.global.exception.ErrorCode.INVALID_LISTING_IMAGE_REFERENCE;
import static setty.global.exception.ErrorCode.LISTING_NOT_FOUND;
import static setty.global.exception.ErrorCode.LISTING_UPDATE_NOT_ALLOWED;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import setty.common.ListingCreated;
import setty.global.exception.BusinessException;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingImage;
import setty.platform.listing.repository.ListingImageRepository;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.listing.storage.ListingImageStorage;

/**
 * 매물 등록·수정의 DB 기록만 트랜잭션으로 묶는다.
 * 사진 업로드 같은 외부 호출은 {@link ListingService}가 이 트랜잭션 밖에서 먼저 끝내고 object key만 넘긴다.
 */
@Component
public class ListingRecorder {

    private static final int MINIMUM_IMAGE_COUNT = 1;
    private static final int MAXIMUM_IMAGE_COUNT = 5;

    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingImageStorage listingImageStorage;
    private final ApplicationEventPublisher eventPublisher;

    public ListingRecorder(
            ListingRepository listingRepository,
            ListingImageRepository listingImageRepository,
            ListingImageStorage listingImageStorage,
            ApplicationEventPublisher eventPublisher
    ) {
        this.listingRepository = listingRepository;
        this.listingImageRepository = listingImageRepository;
        this.listingImageStorage = listingImageStorage;
        this.eventPublisher = eventPublisher;
    }

    static void validateImageCount(int imageCount) {
        if (imageCount < MINIMUM_IMAGE_COUNT || imageCount > MAXIMUM_IMAGE_COUNT) {
            throw new BusinessException(INVALID_LISTING_IMAGE_COUNT);
        }
    }

    @Transactional
    public ListingView.Created create(
            Long sellerId,
            String sellerDongCode,
            ListingCreateCommand command,
            List<String> objectKeys
    ) {
        Listing listing = listingRepository.save(Listing.create(
                sellerId,
                sellerDongCode,
                command.title(),
                command.description(),
                command.price(),
                command.category(),
                command.conditionGrade(),
                command.dimensions()
        ));
        listingRepository.flush();
        listingImageRepository.saveAll(createImages(listing.getId(), objectKeys, 1));
        eventPublisher.publishEvent(new ListingCreated(
                listing.getId(), sellerId, listing.getTitle(), listing.getCreatedAt()));

        return new ListingView.Created(listing.getId(), listing.getCreatedAt());
    }

    /**
     * 업로드 전에 거를 수 있는 요청을 거른다. 잠그지 않고 읽으므로 최종 판정은 {@link #update}가 다시 한다.
     */
    @Transactional(readOnly = true)
    public void validateUpdatable(Long sellerId, Long listingId, ListingUpdateCommand command, List<MultipartFile> newImages) {
        Listing listing = listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .filter(found -> found.isOwnedBy(sellerId))
                .orElseThrow(() -> new BusinessException(LISTING_NOT_FOUND));
        List<ListingImage> currentImages = listingImageRepository.findAllByListingIdOrderByDisplayOrderAsc(listingId);
        List<ListingImage> retainedImages = resolveRetainedImages(currentImages, command.retainedImageIds());
        validateImageCount(retainedImages.size() + newImages.size());
        if (!newImages.isEmpty()) {
            listingImageStorage.validate(newImages);
        }
        if (!listing.canUpdate()) {
            throw new BusinessException(LISTING_UPDATE_NOT_ALLOWED);
        }
    }

    /**
     * @return 수정으로 빠진 사진의 object key. 커밋 뒤 저장소에서 지운다.
     */
    @Transactional
    public List<String> update(Long sellerId, Long listingId, ListingUpdateCommand command, List<String> newObjectKeys) {
        Listing listing = findOwnedActiveForUpdate(sellerId, listingId);
        List<ListingImage> currentImages = listingImageRepository.findAllByListingIdOrderByDisplayOrderAsc(listingId);
        List<ListingImage> retainedImages = resolveRetainedImages(currentImages, command.retainedImageIds());
        validateImageCount(retainedImages.size() + newObjectKeys.size());

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

        listingImageRepository.shiftDisplayOrders(listingId, MAXIMUM_IMAGE_COUNT);
        listingImageRepository.deleteAllByIdInBatch(removedImages.stream().map(ListingImage::getId).toList());

        List<ListingImage> persistedRetainedImages = findImagesByIdsInOrder(retainedImageIds);
        for (int index = 0; index < persistedRetainedImages.size(); index++) {
            persistedRetainedImages.get(index).changeDisplayOrder(index + 1);
        }
        listingImageRepository.saveAll(persistedRetainedImages);
        listingImageRepository.saveAll(createImages(listingId, newObjectKeys, persistedRetainedImages.size() + 1));
        return removedImages.stream().map(ListingImage::getObjectKey).toList();
    }

    private Listing findOwnedActiveForUpdate(Long sellerId, Long listingId) {
        Listing listing = listingRepository.findActiveByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(LISTING_NOT_FOUND));
        if (!listing.isOwnedBy(sellerId)) {
            throw new BusinessException(LISTING_NOT_FOUND);
        }
        return listing;
    }

    private List<ListingImage> resolveRetainedImages(List<ListingImage> currentImages, List<Long> retainedImageIds) {
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

    private List<ListingImage> createImages(Long listingId, List<String> objectKeys, int firstOrder) {
        List<ListingImage> images = new ArrayList<>(objectKeys.size());
        for (int index = 0; index < objectKeys.size(); index++) {
            images.add(ListingImage.create(listingId, objectKeys.get(index), firstOrder + index));
        }
        return images;
    }
}
