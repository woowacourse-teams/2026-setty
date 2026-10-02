const path = require('path');
const webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const enableMsw = process.env.ENABLE_MSW === 'true';
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
                    use: ['style-loader', 'css-loader']
                }
            ]
        },
        plugins: [
            new webpack.DefinePlugin({
                __ENABLE_MSW__: JSON.stringify(enableMsw),
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
            port: 3000
        },
        output: {
            filename: isProduction ? 'assets/js/[name].[contenthash:8].js' : 'bundle.js',
            chunkFilename: isProduction ? 'assets/js/[name].[contenthash:8].js' : '[name].bundle.js',
            cssFilename: isProduction ? 'assets/css/[name].[contenthash:8].css' : 'bundle.css',
            cssChunkFilename: isProduction ? 'assets/css/[name].[contenthash:8].css' : '[name].bundle.css',
            path: path.resolve(__dirname, 'dist'),
            publicPath: '/',
            clean: true
        },
    };
};
