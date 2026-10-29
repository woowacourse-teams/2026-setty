const path = require('path');
const webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const enableMsw = process.env.ENABLE_MSW === 'true';
// 개발 서버가 /api 요청을 넘길 백엔드 주소. 배포에서는 같은 도메인의 /api로 라우팅한다.
const apiProxyTarget = process.env.SETTY_API_URL ?? 'http://localhost:8080';
// 토스페이먼츠 결제위젯 클라이언트 키. 비우면 공개 문서용 테스트 키를 쓴다.
const tossClientKey = process.env.SETTY_TOSS_CLIENT_KEY ?? 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';
const enableSoldListingScenario = process.env.ENABLE_SOLD_LISTING_SCENARIO === 'true';
const enablePriceScenario = process.env.SETTY_ENABLE_PRICE_SCENARIO === 'true';
// PostHog 프로젝트 API 키(phc_…). 비어 있으면 행동 데이터를 수집하지 않는다. buildspec이 SETTY_* 변수를 빌드 해시에 포함한다.
const posthogKey = process.env.SETTY_POSTHOG_KEY ?? '';
const posthogHost = process.env.SETTY_POSTHOG_HOST ?? 'https://us.i.posthog.com';

module.exports = (_, argv) => {
    const isProduction = argv.mode === 'production';

    return {
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
                __ENABLE_MSW__: JSON.stringify(enableMsw),
                __TOSS_CLIENT_KEY__: JSON.stringify(tossClientKey),
                __ENABLE_SOLD_LISTING_SCENARIO__: JSON.stringify(enableSoldListingScenario),
                __ENABLE_PRICE_SCENARIO__: JSON.stringify(enablePriceScenario),
                __POSTHOG_KEY__: JSON.stringify(posthogKey),
                __POSTHOG_HOST__: JSON.stringify(posthogHost)
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
            proxy: enableMsw
                ? []
                : [
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
            clean: true
        }
    };
};
