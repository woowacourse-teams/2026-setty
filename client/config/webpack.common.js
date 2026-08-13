const path = require('node:path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const Dotenv = require('dotenv-webpack');
const root = path.resolve(__dirname, '..');

/**
 * 링크 미리보기의 og:image는 절대 URL이어야 카카오톡 등 크롤러가 이미지를 가져온다.
 * 배포 도메인이 하나로 확정돼 기본값을 코드에 둔다.
 * 다른 도메인으로 빌드할 때만 SETTY_SITE_URL로 덮어쓴다.
 */
const DEFAULT_SITE_ORIGIN = 'https://www.setty.cloud';
const siteOrigin = (process.env.SETTY_SITE_URL || DEFAULT_SITE_ORIGIN)
  .trim()
  .replace(/\/+$/, '');

/** webpack asset module이 돌려주는 값에서 실제 경로만 꺼낸다. */
function toAssetPath(imported) {
  return typeof imported === 'string' ? imported : imported.default;
}

/** webpack asset module이 돌려주는 값에서 실제 경로만 꺼내 절대 URL로 만든다. */
function toOgImageUrl(imported) {
  return `${siteOrigin}${toAssetPath(imported)}`;
}

/**
 * 파비콘은 브라우저·북마크·크롤러가 고정 경로로도 찾으므로 해시 없는 이름 그대로 내보낸다.
 * apple-touch-icon은 크기가 작아 기본 규칙으로는 data URL로 인라인돼 파일이 사라진다.
 */
const FAVICON_FILES = /[\\/]public[\\/](favicon\.(?:svg|ico)|apple-touch-icon\.png)$/i;

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
        test: FAVICON_FILES,
        type: 'asset/resource',
        generator: { filename: '[name][ext]' },
      },
      {
        test: /\.(png|jpe?g|gif|webp|avif)$/i,
        exclude: FAVICON_FILES,
        type: 'asset',
        parser: { dataUrlCondition: { maxSize: 8 * 1024 } },
      },
      {
        test: /\.svg$/i,
        exclude: FAVICON_FILES,
        type: 'asset/resource',
      },
    ],
  },

  plugins: [
    new HtmlWebpackPlugin({
      template: path.join(root, 'public/index.html'),
      templateParameters: { ogImageUrl: toOgImageUrl, assetPath: toAssetPath },
    }),
    new ForkTsCheckerWebpackPlugin(),
    new Dotenv({
      systemvars: true,  
      silent: true, 
    }),
  ],

  cache: { type: 'filesystem' },
};
