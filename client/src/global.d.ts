declare module '*.css';
declare module '*.module.css' {
    const classes: Record<string, string>;
    export default classes;
}

declare const __ENABLE_MSW__: boolean;
