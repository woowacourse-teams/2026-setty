const assert = require('node:assert/strict');
const { test } = require('node:test');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

function renderOrders(state) {
    const source = readFileSync(require('node:path').join(__dirname, '../src/features/orders/OrdersScreen.tsx'), 'utf8');
    const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
    const mod = { exports: {} };
    runInNewContext(code, { module: mod, exports: mod.exports, require(name) {
        if (name === 'react' || name === 'react/jsx-runtime') return require(name);
        if (name === 'react-router') return { useNavigate: () => () => {} };
        if (name.endsWith('.css')) return { default: {} };
        if (name.endsWith('/order')) return { isDeliveryInProgress: () => false };
        if (name.endsWith('/repositories')) return { orderRepository: { getMyOrders: () => {} } };
        if (name.endsWith('/navigation')) return { useNavigateBack: () => () => {}, useNavigateToLogin: () => () => {}, routes: {} };
        if (name.endsWith('/useLoad')) return { useLoad: () => ({ state, fetch: () => {} }) };
        if (name.endsWith('/usePolling')) return { usePolling: () => {} };
        if (name.endsWith('/SupportScreen')) return { OrderSupportWidget: () => React.createElement('span', {}, '상담 위젯') };
        if (name.endsWith('/components')) return new Proxy({}, { get: (_, key) => key === 'Scaffold'
            ? ({ children, floatingActionButton }) => React.createElement('div', {}, floatingActionButton, children)
            : ({ children }) => React.createElement('div', {}, children) });
        return {};
    } });
    return renderToStaticMarkup(React.createElement(mod.exports.OrdersScreen));
}

test('인증 실패 주문 화면에 상담 위젯을 표시하지 않고 인증된 주문 화면에는 표시한다', () => {
    assert.doesNotMatch(renderOrders({ status: 'error', isUnauthorized: true }), /상담 위젯/);
    assert.doesNotMatch(renderOrders({ status: 'loading' }), /상담 위젯/);
    assert.match(renderOrders({ status: 'success', data: [] }), /상담 위젯/);
});
