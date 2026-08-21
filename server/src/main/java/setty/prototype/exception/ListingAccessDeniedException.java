package setty.prototype.exception;

public class ListingAccessDeniedException extends RuntimeException {
    private ListingAccessDeniedException(final String message) {
        super(message);
    }

    public static ListingAccessDeniedException forUpdate() {
        return new ListingAccessDeniedException("해당 매물을 수정할 권한이 없습니다.");
    }

    public static ListingAccessDeniedException forDelete() {
        return new ListingAccessDeniedException("해당 매물을 삭제할 권한이 없습니다.");
    }

    public static ListingAccessDeniedException forMessages() {
        return new ListingAccessDeniedException("해당 매물의 쪽지를 확인할 권한이 없습니다.");
    }
}
