package setty.aisupport;

import static org.mockito.Mockito.mock;

final class SupportServiceFixture {
    private SupportServiceFixture() {}

    static SupportService service(SupportStore store, SupportRuntime runtime, SupportConfiguration.Settings settings) {
        return service(store, runtime, settings, mock(SupportCancellationService.class));
    }

    static SupportService service(SupportStore store, SupportRuntime runtime, SupportConfiguration.Settings settings,
                                  SupportCancellationService cancellation) {
        return new SupportService(store, runtime, settings, cancellation, mock(SupportCancellationResults.class));
    }
}
