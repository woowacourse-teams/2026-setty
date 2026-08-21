package setty.prototype.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("휴대폰 번호 또는 비밀번호가 일치하지 않습니다.");
    }
}
