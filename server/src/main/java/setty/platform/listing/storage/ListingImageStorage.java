package setty.platform.listing.storage;

import java.util.Collection;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 매물 이미지의 원본 파일을 저장하고, DB에 보관할 object key를 반환한다.
 */
public interface ListingImageStorage {

    /**
     * 이미지 개수, 전체 용량, 파일 형식을 검증한다.
     */
    void validate(List<MultipartFile> images);

    /**
     * 이미지를 저장하고 사용자 파일명이 포함되지 않은 object key를 반환한다.
     * 이 메서드가 검증도 수행하므로 호출 전에 {@link #validate(List)}를 중복 호출할 필요가 없다.
     * 일부 업로드 후 실패하면 이 호출에서 저장한 객체를 보상 삭제한다.
     */
    List<String> upload(List<MultipartFile> images);

    /**
     * object key에 해당하는 이미지를 삭제한다.
     */
    void delete(String objectKey);

    /**
     * 여러 이미지를 모두 삭제한다. 한 건이 실패해도 나머지 삭제를 계속 시도한다.
     */
    void deleteAll(Collection<String> objectKeys);

    /**
     * DB에 저장된 object key를 외부에 노출할 공개 URL로 변환한다.
     */
    String publicUrl(String objectKey);
}
