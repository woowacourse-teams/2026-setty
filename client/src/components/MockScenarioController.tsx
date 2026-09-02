import { useState } from 'react';
import {
    getListingMockScenario,
    setListingMockScenario,
    type ListingMockScenario
} from '../mocks/scenario';
import { cx } from '../styles/styles';

export function MockScenarioController() {
    const [scenario, setScenario] = useState(getListingMockScenario);

    function handleChange(nextScenario: ListingMockScenario) {
        setListingMockScenario(nextScenario);
        setScenario(nextScenario);
        window.location.reload();
    }

    return (
        <label className={cx('mock-scenario-controller')}>
            MSW 응답 속도
            <select
                value={scenario}
                onChange={(event) => handleChange(event.target.value as ListingMockScenario)}
            >
                <option value="normal">정상</option>
                <option value="slow">느림 (1.5초)</option>
            </select>
        </label>
    );
}
