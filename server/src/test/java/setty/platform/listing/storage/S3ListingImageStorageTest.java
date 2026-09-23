package setty.platform.listing.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3ListingImageStorageTest {

    private static final String BUCKET = "setty-test-listing-images";
    private static final String PUBLIC_BASE_URL = "https://cdn.test.example/listing-images";
    private static final int MAX_TOTAL_BYTES = 25 * 1024 * 1024;

    @Mock
    private S3Client s3Client;

    private S3ListingImageStorage storage;

    @BeforeEach
    void setUp() {
        ListingImageStorageProperties properties = new ListingImageStorageProperties(
                "ap-northeast-2",
                "  " + BUCKET + "  ",
                "  " + PUBLIC_BASE_URL + "///  "
        );
        storage = new S3ListingImageStorage(s3Client, properties);
    }

    @DisplayName("실제 매직 바이트와 선언 Content-Type이 일치하는 JPEG, PNG, WebP를 허용한다")
    @Test
    void validatesSupportedImageSignatures() {
        List<MultipartFile> images = List.of(
                multipart("sample-chair.jpg", "image/jpeg", jpegBytes()),
                multipart("sample-table.png", "image/png", pngBytes()),
                multipart("sample-desk.webp", "image/webp", webpBytes())
        );

        storage.validate(images);
    }

    @DisplayName("사진은 1장 이상 5장 이하이어야 한다")
    @Test
    void rejectsInvalidImageCounts() {
        assertBusinessError(() -> storage.validate(null), ErrorCode.INVALID_LISTING_IMAGE_COUNT);
        assertBusinessError(() -> storage.validate(List.of()), ErrorCode.INVALID_LISTING_IMAGE_COUNT);

        List<MultipartFile> sixImages = List.of(
                jpeg("sample-1.jpg"),
                jpeg("sample-2.jpg"),
                jpeg("sample-3.jpg"),
                jpeg("sample-4.jpg"),
                jpeg("sample-5.jpg"),
                jpeg("sample-6.jpg")
        );
        assertBusinessError(
                () -> storage.validate(sixImages),
                ErrorCode.INVALID_LISTING_IMAGE_COUNT
        );
    }

    @DisplayName("매직 바이트가 지원 형식이 아니거나 선언 Content-Type과 다르면 거부한다")
    @Test
    void rejectsUnsupportedOrMismatchedImageTypes() {
        MultipartFile fakeJpeg = multipart(
                "sample-chair.jpg",
                "image/jpeg",
                new byte[]{'n', 'o', 't', '-', 'a', 'n', '-', 'i', 'm', 'a', 'g', 'e'}
        );
        assertBusinessError(
                () -> storage.validate(List.of(fakeJpeg)),
                ErrorCode.UNSUPPORTED_LISTING_IMAGE_TYPE
        );

        MultipartFile mismatchedPng = multipart("sample-table.png", "image/jpeg", pngBytes());
        assertBusinessError(
                () -> storage.validate(List.of(mismatchedPng)),
                ErrorCode.UNSUPPORTED_LISTING_IMAGE_TYPE
        );
    }

    @DisplayName("사진 전체 용량은 정확히 25MB까지 허용하고 초과하면 거부한다")
    @Test
    void validatesTotalImageSizeBoundary() {
        storage.validate(List.of(multipart(
                "maximum-size.png",
                "image/png",
                pngBytes(MAX_TOTAL_BYTES)
        )));

        MultipartFile oversizedImage = multipart(
                "oversized.png",
                "image/png",
                pngBytes(MAX_TOTAL_BYTES + 1)
        );
        assertBusinessError(
                () -> storage.validate(List.of(oversizedImage)),
                ErrorCode.LISTING_IMAGE_TOO_LARGE
        );
    }

    @DisplayName("업로드 시 원본 파일명 대신 UUID object key와 감지한 Content-Type을 사용한다")
    @Test
    void uploadsWithUuidObjectKeysAndDetectedContentTypes() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        List<MultipartFile> images = List.of(
                multipart("personal-name-chair.jpg", "image/jpeg", jpegBytes()),
                multipart("sample-table.png", "image/png", pngBytes()),
                multipart("sample-desk.webp", "image/webp", webpBytes())
        );

        List<String> objectKeys = storage.upload(images);

        assertThat(objectKeys).hasSize(3);
        assertThat(objectKeys.get(0)).matches(uuidObjectKeyPattern("jpg"));
        assertThat(objectKeys.get(1)).matches(uuidObjectKeyPattern("png"));
        assertThat(objectKeys.get(2)).matches(uuidObjectKeyPattern("webp"));
        assertThat(objectKeys).noneMatch(key -> key.contains("personal-name"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(3)).putObject(requestCaptor.capture(), any(RequestBody.class));
        List<PutObjectRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).extracting(PutObjectRequest::bucket).containsOnly(BUCKET);
        assertThat(requests).extracting(PutObjectRequest::key).containsExactlyElementsOf(objectKeys);
        assertThat(requests).extracting(PutObjectRequest::contentType)
                .containsExactly("image/jpeg", "image/png", "image/webp");
        assertThat(requests).extracting(PutObjectRequest::contentLength)
                .containsExactly(
                        (long) jpegBytes().length,
                        (long) pngBytes().length,
                        (long) webpBytes().length
                );
    }

    @DisplayName("공개 URL은 설정값의 뒤쪽 슬래시를 제거한 뒤 유효한 object key를 결합한다")
    @Test
    void createsPublicUrlForValidObjectKey() {
        String objectKey = "listings/123e4567-e89b-12d3-a456-426614174000.jpg";

        String publicUrl = storage.publicUrl(objectKey);

        assertThat(publicUrl).isEqualTo(PUBLIC_BASE_URL + "/" + objectKey);
    }

    @DisplayName("Listing 영역 밖의 object key는 조회나 삭제에 사용할 수 없다")
    @Test
    void rejectsInvalidObjectKeys() {
        assertBusinessError(
                () -> storage.publicUrl("members/profile.jpg"),
                ErrorCode.INVALID_LISTING_IMAGE_REFERENCE
        );
        assertBusinessError(
                () -> storage.delete("  "),
                ErrorCode.INVALID_LISTING_IMAGE_REFERENCE
        );

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @DisplayName("두 번째 업로드가 실패하면 시도한 object key를 역순으로 모두 보상 삭제한다")
    @Test
    void compensatesPartialUploadInReverseOrder() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build())
                .thenThrow(S3Exception.builder().message("가상 S3 업로드 실패").build());
        List<MultipartFile> images = List.of(
                jpeg("sample-chair.jpg"),
                multipart("sample-table.png", "image/png", pngBytes())
        );

        assertBusinessError(() -> storage.upload(images), ErrorCode.INTERNAL_ERROR);

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(2)).putObject(putCaptor.capture(), any(RequestBody.class));
        List<String> attemptedKeys = putCaptor.getAllValues().stream()
                .map(PutObjectRequest::key)
                .toList();

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(2)).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getAllValues())
                .extracting(DeleteObjectRequest::bucket)
                .containsOnly(BUCKET);
        assertThat(deleteCaptor.getAllValues())
                .extracting(DeleteObjectRequest::key)
                .containsExactly(attemptedKeys.get(1), attemptedKeys.get(0));
    }

    private static MultipartFile jpeg(String filename) {
        return multipart(filename, "image/jpeg", jpegBytes());
    }

    private static MultipartFile multipart(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("images", filename, contentType, bytes);
    }

    private static byte[] jpegBytes() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 'J', 'F', 'I', 'F'
        };
    }

    private static byte[] pngBytes() {
        return pngBytes(12);
    }

    private static byte[] pngBytes(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    private static byte[] webpBytes() {
        return new byte[]{
                'R', 'I', 'F', 'F', 0x04, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P'
        };
    }

    private static String uuidObjectKeyPattern(String extension) {
        return "listings/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\."
                + extension;
    }

    private static void assertBusinessError(Runnable action, ErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode)
                );
    }
}
