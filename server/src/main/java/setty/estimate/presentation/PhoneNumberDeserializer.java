package setty.estimate.presentation;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class PhoneNumberDeserializer extends ValueDeserializer<String> {
    @Override
    public String deserialize(final JsonParser parser, final DeserializationContext context) {
        final String phoneNumber = parser.getValueAsString();

        if (phoneNumber == null) {
            return null;
        }

        return phoneNumber.replaceAll("[\\s-]", "");
    }
}
