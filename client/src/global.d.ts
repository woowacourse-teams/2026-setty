declare module '*.css';
declare module '*.module.css' {
    const classes: Record<string, string>;
    export default classes;
}

declare const __TOSS_CLIENT_KEY__: string;
declare const __ENABLE_SOLD_LISTING_SCENARIO__: boolean;
declare const __ENABLE_PRICE_SCENARIO__: boolean;
declare const __POSTHOG_KEY__: string;
declare const __POSTHOG_HOST__: string;
declare const __FIREBASE_CONFIG__: { apiKey: string; projectId: string; messagingSenderId: string; appId: string } | null;
declare const __FIREBASE_VAPID_KEY__: string;
