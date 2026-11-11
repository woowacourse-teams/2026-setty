package setty.aisupport;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Versioned, packaged context shared by all model providers. No local-docs runtime dependency. */
final class SupportPrompt {
    private SupportPrompt() {}

    static String instructions() {
        return Content.INSTRUCTIONS;
    }

    static String policy() {
        return Content.POLICY;
    }

    static String scope() {
        return Content.SCOPE;
    }

    static String planner() {
        return Content.PLANNER;
    }

    // Load only when the enabled runtime needs a prompt, not while the disabled app starts.
    private static final class Content {
        private static final String INSTRUCTIONS = read("instructions.md");
        private static final String POLICY = read("cancellation-policy.md");
        private static final String SCOPE = read("scope-reminder.md");
        private static final String PLANNER = read("planner.md");
    }

    private static String read(String name) {
        try {
            return new ClassPathResource("aisupport/" + name)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Packaged support context is missing: " + name, exception);
        }
    }
}
