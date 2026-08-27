import { delay, http, HttpResponse } from 'msw';
import { mockListings } from './listings';
import { getListingMockScenario } from './scenario';

export const handlers = [
    http.get('/api/listings', async () => {
        if (getListingMockScenario() === 'slow') {
            await delay(1500);
        }

        return HttpResponse.json({
            items: mockListings
        });
    })
];
