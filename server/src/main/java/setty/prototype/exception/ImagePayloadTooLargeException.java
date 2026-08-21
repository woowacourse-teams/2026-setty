package setty.prototype.exception;

public class ImagePayloadTooLargeException extends RuntimeException {
    public ImagePayloadTooLargeException() {
        super("사진 전체 용량은 25MB 이하여야 합니다.");
    }
}
