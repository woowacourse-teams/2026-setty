/** 합성 UI 확인 전용. production 번들에 포함되지 않으며 localhost에만 바인딩한다. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../dist');
const stamp = () => new Date().toISOString();
const expires = () => new Date(Date.now() + 30 * 86400000).toISOString();
let sequence = 0, turnSequence = 0;
const conversations = new Map();
const orders = [{ orderId: 101, title: '합성 테스트 책상', createdAt: stamp(), orderStatus: 'CONFIRMED' }, { orderId: 102, title: '합성 테스트 책상', createdAt: null, orderStatus: 'CONFIRMED' }];
function reply(res, status, body) { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(body === undefined ? undefined : JSON.stringify(body)); }
http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    if (url.pathname.startsWith('/api/')) {
        let raw = ''; for await (const chunk of req) raw += chunk;
        const body = raw ? JSON.parse(raw) : {};
        if (url.pathname === '/api/auth/login') return reply(res, 200, { token: 'synthetic-local-only' });
        if (url.pathname === '/api/auth/me') return reply(res, 200, { id: 1, loginId: '합성 테스트', phoneNumber: '01000000000', address: '합성 주소', dongCode: '1111010100' });
        if (req.headers.authorization !== 'Bearer synthetic-local-only') return reply(res, 401, { code: 'INVALID_TOKEN', message: '합성 계정으로 로그인해 주세요.' });
        const match = url.pathname.match(/^\/api\/support\/conversations(?:\/(\d+))?(\/messages)?$/);
        if (!match) return reply(res, 200, []);
        if (!match[1]) {
            if (req.method === 'POST') {
                const item = { id: ++sequence, title: '새 상담', createdAt: stamp(), lastActivityAt: stamp(), expiresAt: expires() };
                conversations.set(item.id, { conversation: item, items: [] }); return reply(res, 200, item);
            }
            return reply(res, 200, { items: [...conversations.values()].map(x => x.conversation).sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt) || b.id - a.id), nextCursor: null });
        }
        const data = conversations.get(Number(match[1]));
        if (!data) return reply(res, 404, { code: 'NOT_FOUND', message: '삭제되었거나 만료된 상담이에요.' });
        if (req.method === 'DELETE') { conversations.delete(data.conversation.id); return reply(res, 204); }
        if (match[2]) {
            const existing = data.items.find(x => x.clientRequestId === body.clientRequestId);
            if (existing) return reply(res, 200, existing);
            const selected = body.input.type === 'SELECT_ORDER';
            const t = { turnId: ++turnSequence, clientRequestId: body.clientRequestId, input: body.input, status: 'SUCCEEDED', createdAt: stamp(), processingDeadlineAt: new Date(Date.now() + 60000).toISOString(), answer: selected ? '현재 배송원 수락 전으로 취소 요청이 가능해요. 이 상담에서는 취소를 실행하지 않아요.' : '같은 이름의 주문이 두 개 있어요. 어떤 주문인지 선택해 주세요.', cards: selected ? [{ type: 'ELIGIBILITY', order: orders.find(x => x.orderId === body.input.orderId), deliveryStatus: 'REQUESTED', eligibility: 'ELIGIBLE', reasonCode: 'BEFORE_DRIVER_ACCEPTANCE', observedAt: stamp(), policyVersion: 'synthetic-fixture' }] : [{ type: 'ORDERS', orders, nextCursor: null, hasUndatedOrders: true }], errorCode: null };
            data.items.unshift(t); data.conversation.lastActivityAt = stamp(); data.conversation.expiresAt = expires();
            if (data.conversation.title === '새 상담') data.conversation.title = body.input.text?.slice(0, 50) || '주문 상담';
            // 문자열에 "응답 유실"을 넣으면 저장 후 연결을 끊어 실제 브라우저 복구를 확인한다.
            if (body.input.text?.includes('응답 유실')) return res.destroy();
            return reply(res, 200, t);
        }
        return reply(res, 200, { ...data, nextCursor: null, activeTurn: null });
    }
    const file = path.resolve(root, `.${decodeURIComponent(url.pathname)}`);
    if (!file.startsWith(root + path.sep) && file !== root) { res.writeHead(403); return res.end(); }
    const target = fs.existsSync(file) && fs.statSync(file).isFile() ? file : path.join(root, 'index.html');
    const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.png': 'image/png' };
    res.writeHead(200, { 'Content-Type': types[path.extname(target)] || 'application/octet-stream' }); fs.createReadStream(target).pipe(res);
}).listen(4174, '127.0.0.1', () => console.log('SYNTHETIC UI ONLY http://127.0.0.1:4174/support — no Bedrock or real API'));
