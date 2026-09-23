package setty.platform.listing.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ListingImageStorageProperties.class)
public class ListingImageStorageConfiguration {

    @Bean
    public S3Client listingImageS3Client(ListingImageStorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region().trim()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
