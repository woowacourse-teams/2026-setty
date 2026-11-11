package setty.aisupport;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Conservative text check for a current CANCEL question; never grants order access or execution by
 * itself.
 */
final class FinalConsentRule {
    static final String VERSION = "final-consent-v2";
    private static final int MAX_LENGTH = 25;
    private static final Set<String> AFFIRMATIONS =
            Set.of("네", "예", "응", "맞아요", "좋아요", "그래요", "넵", "넹", "네네", "예예");
    private static final Set<String> EXECUTIONS =
            Set.of(
                    "취소해 주세요",
                    "취소해주세요",
                    "취소 해주세요",
                    "취소 해 주세요",
                    "취소할게요",
                    "취소해줘",
                    "취소해 줘",
                    "취소 해줘",
                    "취소 해 줘",
                    "진행해 주세요",
                    "진행해주세요");
    private static final Set<String> REFUSAL_MARKERS =
            Set.of(
                    "?", "❓", "❔", "👎", "🙅", "❌", "그런데", "근데", "는데", "면", "말고", "하지 마", "하지마",
                    "안 해", "안해", "잠깐", "아직", "아니", "환불", "수수료", "언제", "얼마", "아마", "고민");
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");
    // Emoji decorations become separators: deleting them must not join otherwise different words.
    private static final Pattern DECORATIONS =
            Pattern.compile(
                    "[.!。！,，\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{200D}\\x{FE0E}\\x{FE0F}]");

    private FinalConsentRule() {}

    static Decision evaluate(String original) {
        if (original == null) {
            return decision(Outcome.NOT_EXPLICIT);
        }
        String text = Normalizer.normalize(original, Normalizer.Form.NFKC);
        if (REFUSAL_MARKERS.stream().anyMatch(text::contains)) {
            return decision(Outcome.NOT_EXPLICIT);
        }
        String normalized =
                WHITESPACE
                        .matcher(DECORATIONS.matcher(text).replaceAll(" "))
                        .replaceAll(" ")
                        .strip()
                        .replaceFirst("[~〜]+$", "")
                        .strip();
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > MAX_LENGTH) {
            return decision(Outcome.NOT_EXPLICIT);
        }
        boolean explicit = EXECUTIONS.contains(normalized)
                || AFFIRMATIONS.stream().anyMatch(affirmation ->
                        normalized.equals(affirmation) || EXECUTIONS.stream().anyMatch(execution ->
                                normalized.equals(affirmation + " " + execution)
                                        || normalized.equals(affirmation + execution)));
        return decision(explicit ? Outcome.EXPLICIT : Outcome.NOT_EXPLICIT);
    }

    private static Decision decision(Outcome outcome) {
        return new Decision(outcome, VERSION);
    }

    enum Outcome {
        EXPLICIT,
        NOT_EXPLICIT
    }

    record Decision(Outcome outcome, String version) {}
}
