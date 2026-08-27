export type ListingMockScenario = 'normal' | 'slow';

const scenarioStorageKey = 'setty:listing-mock-scenario';

export function getListingMockScenario(): ListingMockScenario {
    if (typeof window === 'undefined') {
        return 'normal';
    }

    return window.localStorage.getItem(scenarioStorageKey) === 'slow' ? 'slow' : 'normal';
}

export function setListingMockScenario(scenario: ListingMockScenario) {
    window.localStorage.setItem(scenarioStorageKey, scenario);
}
