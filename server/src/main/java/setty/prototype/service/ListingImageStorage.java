package setty.prototype.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import setty.common.s3.S3ObjectUploader;
import setty.common.s3.S3Properties;
import setty.prototype.exception.ImagePayloadTooLargeException;
import setty.prototype.exception.InvalidImageCountException;
import setty.prototype.exception.InvalidRequestException;
import setty.prototype.exception.UnsupportedImageTypeException;

@Component
public class ListingImageStorage {
    private static final String KEY_PREFIX = "setty/images/listings/";
    private static final int MIN_IMAGE_COUNT = 1;
    private static final int MAX_IMAGE_COUNT = 5;
    private static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final S3ObjectUploader s3ObjectUploader;
    private final S3Properties s3Properties;

    public ListingImageStorage(
            final S3ObjectUploader s3ObjectUploader,
            final S3Properties s3Properties
    ) {
        this.s3ObjectUploader = s3ObjectUploader;
        this.s3Properties = s3Properties;
    }

    public List<String> storeAll(final List<MultipartFile> images) {
        validate(images);

        return images.stream()
                .map(this::store)
                .toList();
    }

    private void validate(final List<MultipartFile> images) {
        if (images == null || images.size() < MIN_IMAGE_COUNT || images.size() > MAX_IMAGE_COUNT) {
            throw new InvalidImageCountException();
        }
        final long totalBytes = images.stream()
                .mapToLong(MultipartFile::getSize)
                .sum();
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new ImagePayloadTooLargeException();
        }
        for (MultipartFile image : images) {
            validateImage(image);
        }
    }

    private void validateImage(final MultipartFile image) {
        if (image.isEmpty()) {
            throw new InvalidRequestException("사진 파일이 비어 있습니다.");
        }
        if (!EXTENSIONS.containsKey(image.getContentType())) {
            throw new UnsupportedImageTypeException();
        }
    }

    private String store(final MultipartFile image) {
        final String key = KEY_PREFIX + UUID.randomUUID() + EXTENSIONS.get(image.getContentType());
        try {
            s3ObjectUploader.upload(image.getBytes(), image.getContentType(), key);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return publicUrlOf(key);
    }

    private String publicUrlOf(final String key) {
        return "https://" + s3Properties.bucketName() + ".s3." + s3Properties.region() + ".amazonaws.com/" + key;
    }
}
