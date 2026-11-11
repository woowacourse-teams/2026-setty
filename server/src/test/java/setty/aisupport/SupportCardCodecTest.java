package setty.aisupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class SupportCardCodecTest {
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @ParameterizedTest
    @ValueSource(strings = {"orders", "order-confirmation", "eligibility", "cancel-confirmation", "receipt", "decline", "out-of-scope"})
    void storedContractPreservesEveryFieldAndItsOrder(String flow) throws Exception {
        var response = json.readTree(Files.readString(Path.of("src/test/resources/aisupport/contracts/" + flow + ".json")));
        for (var original : response.path("cards")) {
            var card = SupportCardCodec.read(original);
            assertThat(json.writeValueAsString(card)).isEqualTo(json.writeValueAsString(original));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"purpose", "ordering", "excludedUndatedCount", "confirmationOnly", "historical"})
    void optionalFieldsRemainAbsentUntilExplicitlyAdded(String field) {
        var source = json.readTree("{\"type\":\"ORDERS\",\"orders\":[],\"nextCursor\":null}");
        var original = SupportCardCodec.read(source);
        var changed = SupportCardCodec.with(original, field, field.equals("excludedUndatedCount") ? 2 : field.equals("purpose") || field.equals("ordering") ? "fixture" : true);
        assertThat(original.wire().has(field)).isFalse();
        assertThat(changed.wire().has(field)).isTrue();
        assertThat(changed.wire().path("nextCursor").isNull()).isTrue();
    }
}
