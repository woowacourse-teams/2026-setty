package setty.dispatch.exception;

public class ItemImageTooLargeException extends RuntimeException {
    public ItemImageTooLargeException() {
        super("물품 사진은 10MB 이하만 올릴 수 있습니다.");
    }
}
