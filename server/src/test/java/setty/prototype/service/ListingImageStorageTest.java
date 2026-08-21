package setty.prototype.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import setty.common.s3.S3ObjectUploader;
import setty.common.s3.S3Properties;
import setty.prototype.exception.ImagePayloadTooLargeException;
import setty.prototype.exception.InvalidImageCountException;
import setty.prototype.exception.UnsupportedImageTypeException;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로토타입 매물 사진 저장")
class ListingImageStorageTest {
    private static final long MEGA_BYTE = 1024L * 1024L;

    @Mock
    private S3ObjectUploader s3ObjectUploader;

    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        listingImageStorage = new ListingImageStorage(
                s3ObjectUploader,
                new S3Properties("ap-northeast-2", "test-bucket")
        );
    }

    @Test
    @DisplayName("사진을 setty/images/listings 키로 저장하고 공개 URL을 돌려준다")
    void storesImagesUnderListingsPrefix() {
        final List<String> urls = listingImageStorage.storeAll(List.of(image("item-1.jpg", "image/jpeg")));

        assertThat(urls).hasSize(1);
        assertThat(urls.getFirst())
                .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/setty/images/listings/")
                .endsWith(".jpg");
    }

    @Test
    @DisplayName("사진이 없으면 저장할 수 없다")
    void rejectsEmptyImageList() {
        assertThatThrownBy(() -> listingImageStorage.storeAll(List.of()))
                .isInstanceOf(InvalidImageCountException.class);
    }

    @Test
    @DisplayName("사진이 5장을 넘으면 저장할 수 없다")
    void rejectsTooManyImages() {
        final List<MultipartFile> images = List.of(
                image("item-1.jpg", "image/jpeg"),
                image("item-2.jpg", "image/jpeg"),
                image("item-3.jpg", "image/jpeg"),
                image("item-4.jpg", "image/jpeg"),
                image("item-5.jpg", "image/jpeg"),
                image("item-6.jpg", "image/jpeg")
        );

        assertThatThrownBy(() -> listingImageStorage.storeAll(images))
                .isInstanceOf(InvalidImageCountException.class);
    }

    @Test
    @DisplayName("사진 전체 용량이 25MB를 넘으면 저장할 수 없다")
    void rejectsTooLargeTotalSize() {
        final List<MultipartFile> images = List.of(largeImage(20 * MEGA_BYTE), largeImage(6 * MEGA_BYTE));

        assertThatThrownBy(() -> listingImageStorage.storeAll(images))
                .isInstanceOf(ImagePayloadTooLargeException.class);
    }

    @Test
    @DisplayName("JPEG·PNG·WebP가 아닌 형식은 저장할 수 없다")
    void rejectsUnsupportedType() {
        final List<MultipartFile> images = List.of(image("item-1.gif", "image/gif"));

        assertThatThrownBy(() -> listingImageStorage.storeAll(images))
                .isInstanceOf(UnsupportedImageTypeException.class);
    }

    private MultipartFile image(final String fileName, final String contentType) {
        return new MockMultipartFile("images", fileName, contentType, "fake-image-bytes".getBytes());
    }

    private MultipartFile largeImage(final long size) {
        final MultipartFile image = mock(MultipartFile.class);
        when(image.getSize()).thenReturn(size);

        return image;
    }
}
