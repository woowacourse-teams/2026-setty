package setty.dispatch.exception;

public class DispatchRequestNotFoundException extends RuntimeException {
    public DispatchRequestNotFoundException() {
        super("배차 요청을 찾을 수 없습니다.");
    }
}
