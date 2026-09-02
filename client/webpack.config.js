const path = require('path');
const webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const enableMsw = process.env.ENABLE_MSW === 'true';

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
                                modules: true
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
                __ENABLE_MSW__: JSON.stringify(enableMsw)
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
