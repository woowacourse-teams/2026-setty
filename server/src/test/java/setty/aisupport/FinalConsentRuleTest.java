package setty.aisupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FinalConsentRuleTest {
    @org.junit.jupiter.api.Test void ruleVersionIsV2() { assertThat(FinalConsentRule.VERSION).isEqualTo("final-consent-v2"); }
    // Table of supported expressions, including normalization. No fuzzy or model-based matching.
    @ParameterizedTest
    @ValueSource(strings = {
            "네", "예", "응", "맞아요", "좋아요", "그래요", "네 취소해주세요", "응 취소해줘",
            "네, 진행해 주세요", "네 취소해 주세요", "네 취소할게요", "네 진행해주세요", "네취소해줘",
            "  네   취소해 주세요!  ", "네. 😊", "응👍🏽", "네\n진행해 주세요", "네\u00A0진행해 주세요",
            "네！", "네, 취소해주세요! 🙏",
            "네 취소 해주세요", "네 취소 해 주세요", "취소 해주세요", "취소 해 주세요",
            "취소해 주세요", "취소해줘", "취소해주세요", "취소할게요",
            "넵", "넹", "네네", "예예", "네~", "네~~", "넹~", "네 취소 해 주세요~",
            "응 취소 해줘", "취소 해 줘", "취소해 줘"
    })
    void acceptsOnlyExplicitConsent(String text) {
        assertThat(FinalConsentRule.evaluate(text))
                .isEqualTo(new FinalConsentRule.Decision(FinalConsentRule.Outcome.EXPLICIT, FinalConsentRule.VERSION));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "네?", "네 근데 환불은 언제야", "무료면 취소해", "취소하지 마", "아니 네", "네네 잠깐만요",
            "음 네 아마도", "네 수수료 있어요?", "취소할지 고민 중이에요", "네 그런데", "네 아직",
            "네 안 해", "네 말고", "네 취소는 하지마", "네 언제", "네 얼마", "좋아요 근데요",
            "네 취소해줘 아니야", "네 대신 다른 주문", "네？", "네❓", "네❔", "네\u200B",
            "네👎", "네🙅", "네❌", "네1", "ㄴ엔넵", "그런말 안했는뎅", "네~ 근데 수수료?", "네~취소해주세요", "네 진행해주세요 please", "예스", "OK",
            "   ", "😊", "네네네네네네네네네네네네네네네네네네네네네네네네네네", "네 진행해 주세요\n하지만 취소하지마"
    })
    void rejectsQuestionsConditionsNegationAndAnythingOutsideAllowlist(String text) {
        assertThat(FinalConsentRule.evaluate(text))
                .isEqualTo(new FinalConsentRule.Decision(FinalConsentRule.Outcome.NOT_EXPLICIT, FinalConsentRule.VERSION));
    }
}
