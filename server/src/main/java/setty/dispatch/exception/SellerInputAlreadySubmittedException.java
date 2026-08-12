package setty.dispatch.exception;

public class SellerInputAlreadySubmittedException extends RuntimeException {
    public SellerInputAlreadySubmittedException() {
        super("이미 판매자 정보가 입력된 링크입니다.");
    }
}
