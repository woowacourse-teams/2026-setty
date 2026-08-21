package setty.prototype.exception;

public class UnsupportedImageTypeException extends RuntimeException {
    public UnsupportedImageTypeException() {
        super("JPEG, PNG, WebP 형식의 사진만 올릴 수 있습니다.");
    }
}
