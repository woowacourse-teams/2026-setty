package setty.prototype.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 가격은 JSON 정수 토큰만 받는다. Jackson 기본 설정은 30000.5 같은 소수점 값을
 * Integer 필드에 30000으로 잘라 넣으므로, 소수점·문자열·불리언을 여기서 400으로 막는다.
 */
public class StrictPriceDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(final JsonParser parser, final DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(Integer.class, "가격은 소수점 없는 정수여야 합니다.");
        }

        return parser.getIntValue();
    }
}
