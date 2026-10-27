package setty.platform.listing.storage;

import static setty.global.exception.ErrorCode.INTERNAL_ERROR;
import static setty.global.exception.ErrorCode.INVALID_LISTING_IMAGE_COUNT;
import static setty.global.exception.ErrorCode.INVALID_LISTING_IMAGE_REFERENCE;
import static setty.global.exception.ErrorCode.LISTING_IMAGE_TOO_LARGE;
import static setty.global.exception.ErrorCode.UNSUPPORTED_LISTING_IMAGE_TYPE;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import setty.global.exception.BusinessException;

/** 스키마 실험의 PNG fixture용 대역. 파일을 저장하거나 외부 저장소에 접근하지 않는다. */
@Component
@Profile("schema-lab")
public class SchemaLabListingImageStorage implements ListingImageStorage {

    private static final String PREFIX = "setty/images/listings/schema-lab/";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;

    @Override
    public void validate(final List<MultipartFile> images) {
        if (images == null || images.isEmpty() || images.size() > 5) {
            throw new BusinessException(INVALID_LISTING_IMAGE_COUNT);
        }
        long totalBytes = 0;
        for (final MultipartFile image : images) {
            if (image == null || image.isEmpty() || image.getContentType() == null
                    || !"image/png".equalsIgnoreCase(image.getContentType().split(";", 2)[0].trim())) {
                throw new BusinessException(UNSUPPORTED_LISTING_IMAGE_TYPE);
            }
            if (image.getSize() > MAX_TOTAL_BYTES - totalBytes) {
                throw new BusinessException(LISTING_IMAGE_TOO_LARGE);
            }
            totalBytes += image.getSize();
            try (var input = image.getInputStream()) {
                if (!Arrays.equals(input.readNBytes(PNG_SIGNATURE.length), PNG_SIGNATURE)) {
                    throw new BusinessException(UNSUPPORTED_LISTING_IMAGE_TYPE);
                }
            } catch (final IOException exception) {
                throw new BusinessException(INTERNAL_ERROR);
            }
        }
    }

    @Override
    public List<String> upload(final List<MultipartFile> images) {
        validate(images);
        return images.stream().map(image -> PREFIX + UUID.randomUUID() + ".png").toList();
    }

    @Override
    public void delete(final String objectKey) {
        validateKey(objectKey);
    }

    @Override
    public void deleteAll(final Collection<String> objectKeys) {
        if (objectKeys == null) {
            throw new BusinessException(INVALID_LISTING_IMAGE_REFERENCE);
        }
        objectKeys.forEach(this::validateKey);
    }

    @Override
    public String publicUrl(final String objectKey) {
        validateKey(objectKey);
        return "https://schema-lab.invalid/" + objectKey;
    }

    private void validateKey(final String objectKey) {
        if (objectKey == null || !objectKey.startsWith(PREFIX)) {
            throw new BusinessException(INVALID_LISTING_IMAGE_REFERENCE);
        }
    }
}
