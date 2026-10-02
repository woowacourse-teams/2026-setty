declare module '*.css';
declare module '*.module.css' {
    const classes: Record<string, string>;
    export default classes;
}

declare const __ENABLE_MSW__: boolean;
declare const __POSTHOG_KEY__: string;
declare const __POSTHOG_HOST__: string;
