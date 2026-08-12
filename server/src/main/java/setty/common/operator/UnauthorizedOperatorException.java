package setty.common.operator;

public class UnauthorizedOperatorException extends RuntimeException {
    public UnauthorizedOperatorException() {
        super("운영자 인증에 실패했습니다.");
    }
}
