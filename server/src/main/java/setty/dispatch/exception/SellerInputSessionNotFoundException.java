package setty.dispatch.exception;

public class SellerInputSessionNotFoundException extends RuntimeException {
    public SellerInputSessionNotFoundException() {
        super("판매자 입력 링크를 찾을 수 없습니다.");
    }
}
