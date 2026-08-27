const path = require('path');
const webpack = require('webpack');

const enableMsw = process.env.ENABLE_MSW === 'true';

module.exports = {
    entry: './src/main.tsx',
    resolve: {
        extensions: ['.tsx', '.ts', '.js']
    },
    module: {
        rules: [
            {
                test: /\.tsx?$/,
                exclude: /node_modules/,
                use: 'ts-loader'
            }
        ]
    },
    plugins: [
        new webpack.DefinePlugin({
            __ENABLE_MSW__: JSON.stringify(enableMsw)
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
        filename: 'bundle.js',
        path: path.resolve(__dirname, 'dist'),
        clean: true
    }
};
