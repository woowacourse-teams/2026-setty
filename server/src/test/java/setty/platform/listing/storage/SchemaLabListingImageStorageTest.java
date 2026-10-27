package setty.platform.listing.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import software.amazon.awssdk.services.s3.S3Client;

class SchemaLabListingImageStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @Test
    void labProfileHasOnlyTheLabStorageAndNoS3Client() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("schema-lab");
            context.register(ListingImageStorageConfiguration.class, S3ListingImageStorage.class,
                    SchemaLabListingImageStorage.class);
            context.refresh();

            assertThat(context.getBeansOfType(ListingImageStorage.class).values())
                    .singleElement().isInstanceOf(SchemaLabListingImageStorage.class);
            assertThat(context.getBeansOfType(S3Client.class)).isEmpty();
        }
    }

    @Test
    void normalProfileKeepsTheS3Storage() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.registerBean("listingImageS3Client", S3Client.class, () -> mock(S3Client.class));
            context.registerBean(ListingImageStorageProperties.class, () -> new ListingImageStorageProperties(
                    "ap-northeast-2", "unused-test-bucket", "https://example.test"));
            context.register(S3ListingImageStorage.class, SchemaLabListingImageStorage.class);
            context.refresh();

            assertThat(context.getBeansOfType(ListingImageStorage.class).values())
                    .singleElement().isInstanceOf(S3ListingImageStorage.class);
        }
    }

    @Test
    void pngFixturesHaveDistinctKeysAndCanBeUsedWithoutAStorageService() {
        var storage = new SchemaLabListingImageStorage();
        var fixture = new MockMultipartFile("images", "fixture.png", "image/png", PNG);

        var keys = storage.upload(List.of(fixture, fixture));

        assertThat(keys).hasSize(2).doesNotHaveDuplicates();
        assertThat(keys).allSatisfy(key -> assertThat(storage.publicUrl(key))
                .startsWith("https://schema-lab.invalid/setty/images/listings/schema-lab/"));
        storage.delete(keys.getFirst());
        storage.deleteAll(keys);
    }

    @Test
    void invalidFixturesFailBeforeTheListingIsStored() {
        var storage = new SchemaLabListingImageStorage();
        var invalid = new MockMultipartFile("images", "fixture.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> storage.upload(List.of(invalid)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_LISTING_IMAGE_TYPE));
        assertThatThrownBy(() -> storage.upload(List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_LISTING_IMAGE_COUNT));
    }
}
