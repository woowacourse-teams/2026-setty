package setty.aisupport;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Explicitly opt in to ONE paid synthetic turn. Never enable for ordinary test runs or CI. */
@EnabledIfEnvironmentVariable(named="SETTY_AI_LIVE_SMOKE_APPROVED", matches=AbstractSupportSmoke.APPROVAL)
class SupportLiveSmokeTest extends AbstractSupportSmoke {
    @Override boolean live() { return true; }
}
