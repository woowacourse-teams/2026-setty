package setty.dispatch.exception;

public class InvalidItemImageException extends RuntimeException {
    public InvalidItemImageException() {
        super("물품 사진은 비어 있지 않은 이미지 파일만 올릴 수 있습니다.");
    }
}
