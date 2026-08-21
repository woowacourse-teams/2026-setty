package setty.prototype.web;

import setty.common.phone.PhoneNumbers;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class NormalizedPhoneNumberDeserializer extends ValueDeserializer<String> {
    @Override
    public String deserialize(final JsonParser parser, final DeserializationContext context) {
        return PhoneNumbers.normalize(parser.getValueAsString());
    }
}
