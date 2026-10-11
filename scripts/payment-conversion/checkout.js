export const CHECKOUT_SELECTOR = '[role="dialog"][aria-label="결제"]';

async function checkoutState(page) {
    return page.evaluate((selector) => {
        const dialog = document.querySelector(selector);
        const button = dialog?.querySelector('button[aria-label="결제 닫기"]');
        return { present: Boolean(dialog), closeButton: Boolean(button) };
    }, CHECKOUT_SELECTOR);
}

// A completed browser click does not guarantee that the app handled it. Read
// the current document after clicking; retry only while the modal still exists.
export async function closeCheckout(page, { loginId, listingId }, timeoutMs = 10_000) {
    const deadline = Date.now() + timeoutMs;
    let state = await checkoutState(page);
    let attempts = 0;
    let lastClickError = '없음';

    while (state.present && attempts < 3 && Date.now() < deadline) {
        attempts++;
        try {
            await page.locator(`${CHECKOUT_SELECTOR} button[aria-label="결제 닫기"]`).click({
                timeout: Math.min(2000, Math.max(1, deadline - Date.now()))
            });
        } catch (error) {
            lastClickError = error instanceof Error ? error.message : String(error);
        }

        const closeDeadline = Math.min(deadline, Date.now() + 1000);
        state = await checkoutState(page);
        while (state.present && Date.now() < closeDeadline) {
            await page.waitForTimeout(Math.max(1, Math.min(100, closeDeadline - Date.now())));
            state = await checkoutState(page);
        }
        if (state.present && attempts < 3 && Date.now() < deadline) {
            console.warn(`결제창이 아직 열려 있어 닫기를 다시 시도합니다. loginId=${loginId}, listingId=${listingId}, attempt=${attempts + 1}`);
        }
    }

    if (state.present) {
        throw new Error(`결제창 닫힘 확인 실패: loginId=${loginId}, listingId=${listingId}, attempts=${attempts}, modalPresent=${state.present}, closeButton=${state.closeButton}, clickError=${lastClickError}`);
    }
}
