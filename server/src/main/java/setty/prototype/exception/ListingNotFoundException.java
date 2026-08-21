package setty.prototype.exception;

public class ListingNotFoundException extends RuntimeException {
    public ListingNotFoundException() {
        super("매물을 찾을 수 없습니다.");
    }
}
