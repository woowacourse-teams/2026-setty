package setty.dispatch.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import setty.common.s3.S3ObjectUploader;
import setty.common.s3.S3Properties;
import setty.dispatch.exception.InvalidItemImageException;
import setty.dispatch.exception.ItemImageTooLargeException;

@Component
public class ItemImageStorage {
    private static final String KEY_PREFIX = "setty/images/items/";
    /**
     * 전역 multipart 한도는 프로토타입 매물 사진(전체 25MB)에 맞춰져 있어
     * 배차 물품 사진 1장 10MB 한도는 여기서 확인한다.
     */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "image/heic", ".heic"
    );

    private final S3ObjectUploader s3ObjectUploader;
    private final S3Properties s3Properties;

    public ItemImageStorage(
            final S3ObjectUploader s3ObjectUploader,
            final S3Properties s3Properties
    ) {
        this.s3ObjectUploader = s3ObjectUploader;
        this.s3Properties = s3Properties;
    }

    public String store(final MultipartFile image) {
        validate(image);
        final String key = KEY_PREFIX + UUID.randomUUID() + extensionOf(image.getContentType());
        try {
            s3ObjectUploader.upload(image.getBytes(), image.getContentType(), key);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return publicUrlOf(key);
    }

    private void validate(final MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidItemImageException();
        }
        final String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidItemImageException();
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new ItemImageTooLargeException();
        }
    }

    private String extensionOf(final String contentType) {
        return EXTENSIONS.getOrDefault(contentType, "");
    }

    private String publicUrlOf(final String key) {
        return "https://" + s3Properties.bucketName() + ".s3." + s3Properties.region() + ".amazonaws.com/" + key;
    }
}
