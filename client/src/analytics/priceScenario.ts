export const PRICE_SCENARIO_STORAGE_KEY = 'setty:price-scenario';

type PriceVariant = 'A' | 'B';
type PriceScenario = Readonly<{
    price_variant: PriceVariant;
    scenario_run_id: string;
}>;

function readPriceScenario(): PriceScenario | null {
    if (!__ENABLE_PRICE_SCENARIO__ || typeof window === 'undefined') return null;

    try {
        const value: unknown = JSON.parse(window.sessionStorage.getItem(PRICE_SCENARIO_STORAGE_KEY) ?? 'null');
        if (!value || typeof value !== 'object' || Array.isArray(value)) return null;

        const { price_variant, scenario_run_id } = value as Record<string, unknown>;
        if (price_variant !== 'A' && price_variant !== 'B') return null;
        if (typeof scenario_run_id !== 'string' || !scenario_run_id.trim()) return null;

        return { price_variant, scenario_run_id: scenario_run_id.trim() };
    } catch {
        return null;
    }
}

// 화면과 이벤트가 같은 조건을 사용하도록 문서를 불러올 때 한 번만 읽는다.
// k6는 페이지 진입 전에 설정하고, 수동 변경 시에는 새로고침한다.
const priceScenario = readPriceScenario();

export function getPriceVariant(): PriceVariant {
    return priceScenario?.price_variant ?? 'B';
}

export function getPriceScenarioEventProperties(): Record<string, string | boolean> {
    return priceScenario ? { ...priceScenario, is_synthetic: true } : {};
}
