package setty.platform.listing.storage;

import static setty.global.exception.ErrorCode.INTERNAL_ERROR;
import static setty.global.exception.ErrorCode.INVALID_LISTING_IMAGE_COUNT;
import static setty.global.exception.ErrorCode.INVALID_LISTING_IMAGE_REFERENCE;
import static setty.global.exception.ErrorCode.LISTING_IMAGE_TOO_LARGE;
import static setty.global.exception.ErrorCode.UNSUPPORTED_LISTING_IMAGE_TYPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import setty.global.exception.BusinessException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3ListingImageStorage implements ListingImageStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ListingImageStorage.class);
    private static final int MIN_IMAGE_COUNT = 1;
    private static final int MAX_IMAGE_COUNT = 5;
    private static final long MAX_TOTAL_BYTES = 25L * 1024L * 1024L;
    private static final String OBJECT_KEY_PREFIX = "listings/";

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3ListingImageStorage(
            @Qualifier("listingImageS3Client") S3Client s3Client,
            ListingImageStorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket().trim();
        this.publicBaseUrl = removeTrailingSlashes(properties.publicBaseUrl().trim());
    }

    @Override
    public void validate(List<MultipartFile> images) {
        prepare(images);
    }

    @Override
    public List<String> upload(List<MultipartFile> images) {
        List<PreparedImage> preparedImages = prepare(images);
        List<String> uploadedObjectKeys = new ArrayList<>(preparedImages.size());

        try {
            for (PreparedImage image : preparedImages) {
                String objectKey = createObjectKey(image.type());
                uploadedObjectKeys.add(objectKey);
                put(objectKey, image);
            }
            return List.copyOf(uploadedObjectKeys);
        } catch (BusinessException exception) {
            compensateUploads(uploadedObjectKeys);
            throw exception;
        } catch (SdkException exception) {
            log.error("매물 이미지 업로드에 실패했습니다", exception);
            compensateUploads(uploadedObjectKeys);
            throw new BusinessException(INTERNAL_ERROR);
        }
    }

    @Override
    public void delete(String objectKey) {
        validateObjectKey(objectKey);

        try {
            deleteFromS3(objectKey);
        } catch (SdkException exception) {
            log.error("매물 이미지 삭제에 실패했습니다. objectKey={}", objectKey, exception);
            throw new BusinessException(INTERNAL_ERROR);
        }
    }

    @Override
    public void deleteAll(Collection<String> objectKeys) {
        if (objectKeys == null) {
            throw new BusinessException(INVALID_LISTING_IMAGE_REFERENCE);
        }

        BusinessException invalidReference = null;
        boolean s3Failure = false;
        for (String objectKey : objectKeys) {
            try {
                validateObjectKey(objectKey);
                deleteFromS3(objectKey);
            } catch (BusinessException exception) {
                invalidReference = exception;
            } catch (SdkException exception) {
                log.error("매물 이미지 일괄 삭제 중 실패했습니다. objectKey={}", objectKey, exception);
                s3Failure = true;
            }
        }

        if (s3Failure) {
            throw new BusinessException(INTERNAL_ERROR);
        }
        if (invalidReference != null) {
            throw invalidReference;
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        validateObjectKey(objectKey);
        return publicBaseUrl + "/" + objectKey;
    }

    private List<PreparedImage> prepare(List<MultipartFile> images) {
        validateCount(images);

        long totalBytes = 0L;
        List<PreparedImage> preparedImages = new ArrayList<>(images.size());
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                throw new BusinessException(UNSUPPORTED_LISTING_IMAGE_TYPE);
            }

            long imageSize = image.getSize();
            if (imageSize > MAX_TOTAL_BYTES - totalBytes) {
                throw new BusinessException(LISTING_IMAGE_TOO_LARGE);
            }
            totalBytes += imageSize;

            byte[] bytes = readBytes(image);
            ImageType actualType = ImageType.detect(bytes);
            if (actualType == null || !actualType.matchesContentType(image.getContentType())) {
                throw new BusinessException(UNSUPPORTED_LISTING_IMAGE_TYPE);
            }
            preparedImages.add(new PreparedImage(actualType, bytes));
        }
        return preparedImages;
    }

    private void validateCount(List<MultipartFile> images) {
        if (images == null || images.size() < MIN_IMAGE_COUNT || images.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(INVALID_LISTING_IMAGE_COUNT);
        }
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException exception) {
            log.error("매물 이미지 파일을 읽지 못했습니다", exception);
            throw new BusinessException(INTERNAL_ERROR);
        }
    }

    private void put(String objectKey, PreparedImage image) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(image.type().contentType)
                .contentLength((long) image.bytes().length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(image.bytes()));
    }

    private void deleteFromS3(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        s3Client.deleteObject(request);
    }

    private void compensateUploads(List<String> uploadedObjectKeys) {
        List<String> reverseOrder = new ArrayList<>(uploadedObjectKeys);
        Collections.reverse(reverseOrder);
        for (String objectKey : reverseOrder) {
            try {
                deleteFromS3(objectKey);
            } catch (SdkException exception) {
                log.error("매물 이미지 업로드 보상 삭제에 실패했습니다. objectKey={}", objectKey, exception);
            }
        }
    }

    private String createObjectKey(ImageType imageType) {
        return OBJECT_KEY_PREFIX + UUID.randomUUID() + "." + imageType.extension;
    }

    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith(OBJECT_KEY_PREFIX)) {
            throw new BusinessException(INVALID_LISTING_IMAGE_REFERENCE);
        }
    }

    private static String removeTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private record PreparedImage(ImageType type, byte[] bytes) {
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        private boolean matchesContentType(String declaredContentType) {
            if (declaredContentType == null) {
                return false;
            }
            String normalized = declaredContentType
                    .split(";", 2)[0]
                    .trim()
                    .toLowerCase(Locale.ROOT);
            return contentType.equals(normalized)
                    || (this == JPEG && "image/jpg".equals(normalized));
        }

        private static ImageType detect(byte[] bytes) {
            if (isJpeg(bytes)) {
                return JPEG;
            }
            if (isPng(bytes)) {
                return PNG;
            }
            if (isWebp(bytes)) {
                return WEBP;
            }
            return null;
        }

        private static boolean isJpeg(byte[] bytes) {
            return bytes.length >= 3
                    && unsigned(bytes[0]) == 0xFF
                    && unsigned(bytes[1]) == 0xD8
                    && unsigned(bytes[2]) == 0xFF;
        }

        private static boolean isPng(byte[] bytes) {
            int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            if (bytes.length < signature.length) {
                return false;
            }
            for (int index = 0; index < signature.length; index++) {
                if (unsigned(bytes[index]) != signature[index]) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isWebp(byte[] bytes) {
            return bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P';
        }

        private static int unsigned(byte value) {
            return Byte.toUnsignedInt(value);
        }
    }
}
