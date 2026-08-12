const path = require('node:path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const Dotenv = require('dotenv-webpack');
const root = path.resolve(__dirname, '..');

/**
 * 링크 미리보기의 og:image는 절대 URL이어야 카카오톡 등 크롤러가 이미지를 가져온다.
 * 배포 주소를 모르는 로컬 빌드에서는 값을 비워 두고 상대 경로로 둔다.
 */
const siteOrigin = (process.env.SETTY_SITE_URL ?? '').trim().replace(/\/+$/, '');

/** webpack asset module이 돌려주는 값에서 실제 경로만 꺼내 절대 URL로 만든다. */
function toOgImageUrl(imported) {
  const assetPath = typeof imported === 'string' ? imported : imported.default;
  return `${siteOrigin}${assetPath}`;
}

/** @type {import('webpack').Configuration} */
module.exports = {
  entry: path.join(root, 'src/index.tsx'),

  output: {
    path: path.join(root, 'dist'),
    publicPath: '/',
    clean: true,
  },

  resolve: {
    extensions: ['.tsx', '.ts', '.jsx', '.js'],
    alias: { '@': path.join(root, 'src') },
  },

  module: {
    rules: [
      {
        test: /\.[jt]sx?$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: { cacheDirectory: true },
        },
      },
      {
        test: /\.(png|jpe?g|gif|webp|avif)$/i,
        type: 'asset',
        parser: { dataUrlCondition: { maxSize: 8 * 1024 } },
      },
      {
        test: /\.svg$/i,
        type: 'asset/resource',
      },
    ],
  },

  plugins: [
    new HtmlWebpackPlugin({
      template: path.join(root, 'public/index.html'),
      templateParameters: { ogImageUrl: toOgImageUrl },
    }),
    new ForkTsCheckerWebpackPlugin(),
    new Dotenv({
      systemvars: true,  
      silent: true, 
    }),
  ],

  cache: { type: 'filesystem' },
};
