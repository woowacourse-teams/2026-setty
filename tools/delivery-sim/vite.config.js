import os from 'node:os';
import { defineConfig } from 'vite';

// 폰이 노트북의 client 개발 서버에 붙을 수 있게 QR 기본 주소는 노트북의 LAN IP로 잡는다.
function lanIp() {
    for (const list of Object.values(os.networkInterfaces())) {
        for (const net of list ?? []) {
            if (net.family === 'IPv4' && !net.internal) return net.address;
        }
    }
    return 'localhost';
}

const apiTarget = process.env.SIM_API_URL ?? 'http://localhost:8080';
const osrmTarget = process.env.SIM_OSRM_URL ?? 'http://localhost:5008';
const clientUrl = process.env.SIM_CLIENT_URL ?? `http://${lanIp()}:3000`;
// 남쪽 위도, 서쪽 경도, 가로 칸 수, 세로 칸 수. 기본은 동작구 상도3·4동 일대.
const area = (process.env.SIM_AREA ?? '37.4945,126.9265,8,5').split(',').map(Number);

export default defineConfig({
    define: {
        __SIM_CONFIG__: JSON.stringify({ apiTarget, clientUrl, area })
    },
    server: {
        port: 5180,
        proxy: {
            // 서버 CORS 허용 목록에 시뮬레이터 주소가 없으므로 Origin을 떼고 같은 출처 요청처럼 넘긴다.
            '/api': {
                target: apiTarget,
                changeOrigin: true,
                configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin'))
            },
            '/osrm': { target: osrmTarget, changeOrigin: true, rewrite: (path) => path.replace(/^\/osrm/, '') }
        }
    }
});
