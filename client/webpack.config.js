const path = require('path');
const webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

// 개발 서버가 /api 요청을 넘길 백엔드 주소. 배포에서는 같은 도메인의 /api로 라우팅한다.
const apiProxyTarget = process.env.SETTY_API_URL ?? 'http://localhost:8080';
// 토스페이먼츠 결제위젯 클라이언트 키. 비우면 공개 문서용 테스트 키를 쓴다.
const tossClientKey = process.env.SETTY_TOSS_CLIENT_KEY ?? 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';
const enableSoldListingScenario = process.env.ENABLE_SOLD_LISTING_SCENARIO === 'true';
const enablePriceScenario = process.env.SETTY_ENABLE_PRICE_SCENARIO === 'true';
// PostHog 프로젝트 API 키(phc_…). 비어 있으면 행동 데이터를 수집하지 않는다. buildspec이 SETTY_* 변수를 빌드 해시에 포함한다.
const posthogKey = process.env.SETTY_POSTHOG_KEY ?? '';
const posthogHost = process.env.SETTY_POSTHOG_HOST ?? 'https://us.i.posthog.com';
// Firebase 웹 앱 설정(공개값)과 웹 푸시 VAPID 공개키. 넷 중 하나라도 비면 푸시 기능을 숨긴다.
const firebaseConfig = ['SETTY_FIREBASE_API_KEY', 'SETTY_FIREBASE_PROJECT_ID', 'SETTY_FIREBASE_MESSAGING_SENDER_ID', 'SETTY_FIREBASE_APP_ID']
    .every((name) => process.env[name])
    ? {
        apiKey: process.env.SETTY_FIREBASE_API_KEY,
        projectId: process.env.SETTY_FIREBASE_PROJECT_ID,
        messagingSenderId: process.env.SETTY_FIREBASE_MESSAGING_SENDER_ID,
        appId: process.env.SETTY_FIREBASE_APP_ID
    }
    : null;
const firebaseVapidKey = process.env.SETTY_FIREBASE_VAPID_KEY ?? '';
const SERVICE_WORKER_FILE = 'firebase-messaging-sw.js';

module.exports = (_, argv) => {
    const isProduction = argv.mode === 'production';

    return [appConfig(isProduction), serviceWorkerConfig(isProduction)];
};

// 앱. 개발 서버 설정은 첫 번째 설정에서만 읽힌다.
// 이름이 있어야 페이지의 개발 서버 클라이언트가 서비스 워커 빌드 신호를 무시한다. 없으면 페이지가 무한히 새로고침된다.
function appConfig(isProduction) {
    return {
        name: 'app',
        entry: './src/main.tsx',
        cache: isProduction
            ? {
                type: 'filesystem',
                cacheDirectory: path.resolve(__dirname, '.webpack-cache')
            }
            : false,
        resolve: {
            extensions: ['.tsx', '.ts', '.js']
        },
        module: {
            rules: [
                {
                    test: /\.tsx?$/,
                    exclude: /node_modules/,
                    use: 'ts-loader'
                },
                {
                    test: /\.module\.css$/,
                    use: [
                        'style-loader',
                        {
                            loader: 'css-loader',
                            options: {
                                modules: {
                                    namedExport: false,
                                    exportLocalsConvention: 'as-is'
                                }
                            }
                        }
                    ]
                },
                {
                    test: /\.css$/,
                    exclude: /\.module\.css$/,
                    use: [
                        'style-loader',
                        {
                            loader: 'css-loader',
                            // /fonts/처럼 /로 시작하는 주소는 public에서 그대로 복사되는 파일이라 번들하지 않는다
                            options: { url: { filter: (url) => !url.startsWith('/') } }
                        }
                    ]
                }
            ]
        },
        plugins: [
            new webpack.DefinePlugin({
                __TOSS_CLIENT_KEY__: JSON.stringify(tossClientKey),
                __ENABLE_SOLD_LISTING_SCENARIO__: JSON.stringify(enableSoldListingScenario),
                __ENABLE_PRICE_SCENARIO__: JSON.stringify(enablePriceScenario),
                __POSTHOG_KEY__: JSON.stringify(posthogKey),
                __POSTHOG_HOST__: JSON.stringify(posthogHost),
                __FIREBASE_CONFIG__: JSON.stringify(firebaseConfig),
                __FIREBASE_VAPID_KEY__: JSON.stringify(firebaseVapidKey)
            }),
            new HtmlWebpackPlugin({
                template: path.resolve(__dirname, 'public/index.html')
            }),
            new CopyWebpackPlugin({
                patterns: [
                    {
                        from: path.resolve(__dirname, 'public'),
                        to: '.',
                        globOptions: {
                            ignore: ['**/index.html']
                        }
                    }
                ]
            })
        ],
        devServer: {
            static: {
                directory: path.resolve(__dirname, 'public')
            },
            historyApiFallback: true,
            host: '0.0.0.0',
            port: 3000,
            proxy: [
                {
                    context: ['/api'],
                    target: apiProxyTarget,
                    changeOrigin: true
                }
            ]
        },
        output: {
            filename: isProduction ? 'assets/js/[name].[contenthash:8].js' : 'bundle.js',
            chunkFilename: isProduction ? 'assets/js/[name].[contenthash:8].js' : '[name].bundle.js',
            path: path.resolve(__dirname, 'dist'),
            publicPath: '/',
            // 서비스 워커는 다른 설정이 같은 폴더에 내보내므로 지우지 않는다
            clean: { keep: SERVICE_WORKER_FILE }
        }
    };
}

// FCM 서비스 워커. 브라우저가 고정 주소로 등록하므로 루트에 해시 없이 내보낸다.
// 웹 대상이 아니게(target: false) 따로 빌드해 개발 서버가 새로고침 클라이언트를 끼워 넣지 않게 한다.
// 그 코드는 window를 찾아 서비스 워커 등록을 깨뜨린다.
function serviceWorkerConfig(isProduction) {
    return {
        name: 'service-worker',
        target: false,
        entry: './src/firebase-messaging-sw.js',
        devtool: isProduction ? false : 'cheap-source-map',
        resolve: {
            conditionNames: ['browser', 'import', 'module', 'default'],
            mainFields: ['browser', 'module', 'main']
        },
        plugins: [
            new webpack.DefinePlugin({
                __FIREBASE_CONFIG__: JSON.stringify(firebaseConfig)
            })
        ],
        output: {
            filename: SERVICE_WORKER_FILE,
            path: path.resolve(__dirname, 'dist'),
            publicPath: '/',
            globalObject: 'self',
            iife: true,
            chunkFormat: false,
            chunkLoading: false,
            wasmLoading: false,
            workerChunkLoading: false,
            environment: {
                arrowFunction: true,
                const: true,
                destructuring: true,
                forOf: true,
                optionalChaining: true,
                templateLiteral: true
            }
        }
    };
}
