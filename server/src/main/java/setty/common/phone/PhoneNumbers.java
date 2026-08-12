package setty.common.phone;

public final class PhoneNumbers {
    private static final int NORMALIZED_LENGTH = 11;

    private PhoneNumbers() {
    }

    public static String normalize(final String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        return phoneNumber.replaceAll("[\\s-]", "");
    }

    public static String format(final String phoneNumber) {
        final String normalized = normalize(phoneNumber);
        if (normalized == null || normalized.length() != NORMALIZED_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, 3)
                + "-"
                + normalized.substring(3, 7)
                + "-"
                + normalized.substring(7);
    }
}
