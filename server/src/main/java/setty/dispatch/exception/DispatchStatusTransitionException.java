package setty.dispatch.exception;

import setty.dispatch.domain.DispatchStatus;

public class DispatchStatusTransitionException extends RuntimeException {
    private final DispatchStatus current;
    private final DispatchStatus attempted;

    public DispatchStatusTransitionException(final DispatchStatus current, final DispatchStatus attempted) {
        super("현재 상태에서 요청한 상태로 변경할 수 없습니다.");
        this.current = current;
        this.attempted = attempted;
    }

    public DispatchStatus getCurrent() {
        return current;
    }

    public DispatchStatus getAttempted() {
        return attempted;
    }
}
