import { useEffect } from 'react';
import { deliveryRepository } from '../../api/deliveryRepository';
import { deliverySimulatorStore } from '../../api/http';
import { gridCellOf } from '../../model/driverLocation';

const SEND_INTERVAL_MILLIS = 10_000;

/**
 * 화면이 앞에 있는 동안 폰 위치를 서버에 보낸다. 칸이 바뀌면 바로, 아니면 10초마다 보낸다.
 * 서버는 2분 동안 위치가 없으면 칸을 잊으므로 같은 칸이어도 주기적으로 보낸다.
 * 시뮬레이터가 위치를 대신 보내는 탭이면 아무것도 하지 않는다.
 */
export function useDriverLocation() {
    useEffect(() => {
        if (deliverySimulatorStore.get() || !('geolocation' in navigator)) return undefined;
        let watchId: number | null = null;
        let latest: GeolocationCoordinates | null = null;
        let sentCell: number | null = null;
        let sentAt = 0;
        let resendTimer: ReturnType<typeof setTimeout> | null = null;

        const clearResend = () => {
            if (resendTimer !== null) clearTimeout(resendTimer);
            resendTimer = null;
        };
        // 제자리에 서 있으면 watchPosition이 새 값을 주지 않으므로 보낸 때부터 10초가 되면 마지막 위치를 다시 보낸다
        const scheduleResend = (delayMillis: number) => {
            clearResend();
            resendTimer = setTimeout(() => {
                resendTimer = null;
                if (latest && document.visibilityState === 'visible') send(latest);
            }, delayMillis);
        };
        const send = (coords: GeolocationCoordinates) => {
            const cell = gridCellOf(coords.latitude, coords.longitude);
            if (cell === null) return;
            sentCell = cell;
            sentAt = Date.now();
            scheduleResend(SEND_INTERVAL_MILLIS);
            // 위치 전송 실패는 다음 전송에서 회복되므로 화면에 알리지 않는다
            deliveryRepository.sendLocation(coords.latitude, coords.longitude).catch(() => {});
        };

        const start = () => {
            if (watchId !== null) return;
            watchId = navigator.geolocation.watchPosition((position) => {
                latest = position.coords;
                const cell = gridCellOf(latest.latitude, latest.longitude);
                const elapsed = Date.now() - sentAt;
                if (cell !== sentCell || elapsed >= SEND_INTERVAL_MILLIS) {
                    send(latest);
                } else if (resendTimer === null) {
                    // 숨겼다 다시 보이면 stop이 지운 재전송을 남은 시간만큼 다시 건다
                    scheduleResend(SEND_INTERVAL_MILLIS - elapsed);
                }
            }, () => {}, { enableHighAccuracy: true, maximumAge: 5_000 });
        };
        const stop = () => {
            if (watchId !== null) navigator.geolocation.clearWatch(watchId);
            watchId = null;
            clearResend();
        };
        const onVisibilityChange = () => (document.visibilityState === 'visible' ? start() : stop());

        if (document.visibilityState === 'visible') start();
        document.addEventListener('visibilitychange', onVisibilityChange);
        return () => {
            stop();
            document.removeEventListener('visibilitychange', onVisibilityChange);
        };
    }, []);
}
