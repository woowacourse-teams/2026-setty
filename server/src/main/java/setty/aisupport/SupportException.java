package setty.aisupport;

final class SupportException extends RuntimeException {
    final int status;
    final String code;
    SupportDtos.Result evidence;

    SupportException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
