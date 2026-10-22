package setty.address;

public record AddressResolution(Status status, StandardAddress address) {

    public enum Status {
        MATCHED, AMBIGUOUS, UNMATCHED
    }

    static AddressResolution matched(final StandardAddress address) {
        return new AddressResolution(Status.MATCHED, address);
    }

    static AddressResolution ambiguous() {
        return new AddressResolution(Status.AMBIGUOUS, null);
    }

    static AddressResolution unmatched() {
        return new AddressResolution(Status.UNMATCHED, null);
    }
}
