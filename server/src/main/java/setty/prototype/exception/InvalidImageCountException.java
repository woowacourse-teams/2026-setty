package setty.prototype.exception;

public class InvalidImageCountException extends RuntimeException {
    public InvalidImageCountException() {
        super("사진은 1장 이상 5장 이하로 올릴 수 있습니다.");
    }
}
