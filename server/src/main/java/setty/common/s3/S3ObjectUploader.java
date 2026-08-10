package setty.common.s3;

import java.nio.file.Path;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3ObjectUploader {
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3ObjectUploader(
            final S3Client s3Client,
            final S3Properties s3Properties
    ) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    public void upload(final Path sourceFile, final String key) {
        final PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .build();

        s3Client.putObject(request, RequestBody.fromFile(sourceFile));
    }

    public void upload(final byte[] content, final String contentType, final String key) {
        final PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
    }
}
