# Responsive Design QA

## Comparison target

- Source visual truth: `/var/folders/ws/38tjd5ss4vb4_mxxdttkkclc0000gn/T/codex-clipboard-1634284e-41fb-4145-a92f-dc8d42566c88.png`
- Browser-rendered implementation: `/private/tmp/setty-responsive-home-final-390x844.png`
- Full-view comparison: `/private/tmp/setty-responsive-comparison-vertical-390x1688.png`
- Focused header/card comparison: `/private/tmp/setty-responsive-focused-comparison-780x520.png`
- State: mobile home, menu closed, normal MSW response

## Viewport and normalization

- Source pixels: 638 × 1366.
- Implementation pixels: 390 × 844.
- CSS viewport: 390 × 844; `devicePixelRatio: 1`.
- The source was aspect-ratio-preserving downscaled to 390 × 835 and padded with 9 px of white space at the bottom to produce a 390 × 844 comparison frame. The implementation was not rescaled.

## Findings

No actionable P0, P1, or P2 differences remain.

- Fonts and typography: the existing SETTY system-sans stack and strong product hierarchy remain intact. Mobile titles, prices, shipping cost, totals, and metadata stay readable without overlap or broken wrapping.
- Spacing and layout rhythm: the mobile page uses 16 px outer gutters, a 12 px card gap, 173 px cards at 390 px, a compact brand/action row, and a full-width rounded search surface. These match the reference's mobile density and two-column shape while omitting its product-specific location/category/filter controls as intentionally requested.
- Colors and visual tokens: SETTY's neutral black, warm gray, and off-white language is preserved instead of copying the reference's orange branding. Borders, radii, and muted metadata remain consistent with the existing product.
- Image quality and asset fidelity: existing SETTY furniture assets are used with square mobile crops and `object-fit: cover`; there are no placeholder or generated substitutes. UI icons use the Phosphor icon family rather than handcrafted SVG/CSS shapes.
- Copy and content: all visible copy remains SETTY-specific. Reference-only marketplace location and category copy was not copied.
- Responsiveness: body width matched the viewport at 360, 390, 414, 768, 1024, and 1440 px. Product columns resolved to 2, 2, 2, 2, 3, and 4 respectively, with no horizontal overflow.
- Accessibility and interaction: login and logout are directly exposed as 44 px mobile controls. The authenticated mobile menu contains only `내 가구` and `내 주문`, overlays rather than reflows the catalog, exposes `aria-expanded`/`aria-controls`, and closes on outside click or Escape. The authentication sheet traps focus, locks background scrolling, restores prior focus, and marks the background inert. Reduced-motion users do not receive skeleton animation.

## Additional screen evidence

- Detail: `/private/tmp/setty-responsive-detail-390.png`
- Authentication sheet: `/private/tmp/setty-responsive-auth-390.png`
- Desktop login without unnecessary scrollbar: `/private/tmp/setty-auth-desktop-no-scroll-1329x647.png`
- Landscape authentication sheet with required overflow: `/private/tmp/setty-auth-landscape-scroll-844x390-v2.png`
- Mobile guest direct login: `/private/tmp/setty-mobile-guest-direct-login-440x956.png`
- Mobile authentication sheet: `/private/tmp/setty-mobile-auth-440x956.png`
- Mobile authenticated actions: `/private/tmp/setty-mobile-logged-in-actions-440x956.png`
- Mobile menu covering the search surface: `/private/tmp/setty-mobile-menu-covers-search-440x956.png`
- Restored catalog position: `/private/tmp/setty-responsive-scroll-restored-390x400.png`
- My listings: `/private/tmp/setty-responsive-my-listings-390.png`
- Registration: `/private/tmp/setty-responsive-registration-390.png`
- Orders: `/private/tmp/setty-responsive-orders-390.png`
- Tablet home: `/private/tmp/setty-responsive-home-768.png`
- Desktop home: `/private/tmp/setty-responsive-home-1440.png`

Primary interactions tested: direct mobile login and logout, overlay menu open/close, outside-click and Escape dismissal, listing selection, detail back action, purchase-to-auth flow, signup/login with virtual data, authentication focus behavior, my-listings navigation, registration layout, order creation, and my-orders navigation. Browser console errors: none.

## Comparison history

- Formal pass 1: the normalized full-view and focused comparison showed the intended compact header, rounded search surface, two-column card proportions, and SETTY visual language with no visual P0/P1/P2 mismatch. The interaction audit found one P2 issue: opening authentication from the mobile menu could restore focus to the now-hidden login button.
- Post-comparison fix: `Header.runMenuAction` now returns focus to the visible menu toggle before opening authentication. Escape from the authentication sheet was rechecked with `activeIsMenuButton: true`, `menuExpanded: false`, and the original body overflow restored.
- Formal pass 2: read-only review found one P1 short-viewport issue (the desktop-width authentication dialog could be clipped in landscape) and one P2 navigation issue (detail-to-list returned to the top instead of the prior catalog position).
- Post-review fixes: the base authentication dialog now has viewport-height overflow protection, and the catalog captures its scroll position before detail navigation and restores it only for the `목록으로` action.
- Formal pass 3: at 844 × 390 the authentication dialog exposed an internal scrollbar and the login submit control remained reachable; at 390 × 400 the detail back action restored the previous catalog position. The mobile home was recaptured and both visual comparison frames were regenerated with no regressions. No actionable P0/P1/P2 visual or interaction differences remain.
- Formal pass 4: user review found three P2 interaction issues: the normal-height desktop login dialog showed an unnecessary scrollbar, mobile login/logout were hidden inside the account menu, and opening the authenticated menu pushed the catalog downward.
- Post-feedback fixes: desktop dialog bounds now account for the existing `zoom: 0.7`, so overflow appears only when content truly exceeds the available height; mobile login/logout are direct header actions; the authenticated dropdown is absolutely positioned over the catalog and closes on outside click or Escape.
- Formal pass 5: at 1329 × 647 the complete login form rendered without a scrollbar; at 844 × 390 required internal overflow remained usable; at 440 × 956 the guest and authenticated header states exposed direct auth actions, and the open dropdown covered the search surface and unchanged catalog position instead of reflowing either region. The 390 × 844 source/implementation comparison was regenerated. No actionable P0/P1/P2 visual or interaction differences remain.

## Verification

- `npm run type-check`: passed.
- `npm run build`: passed with webpack's entrypoint-size advisory (262 KiB combined JS/CSS).

## Follow-up polish

- No blocking polish remains. Additional real API listings will naturally fill subsequent mobile grid rows; the current normal mock contains two listings.

final result: passed
