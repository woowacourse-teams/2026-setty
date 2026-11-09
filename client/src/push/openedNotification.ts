import { useEffect } from 'react';
import { useSearchParams } from 'react-router';
import { notificationRepository } from '../api/repositories';
import { notificationTypeOf } from '../model/notification';
import { readNotification } from './unreadNotifications';

// 서비스 워커가 알림을 누른 주소에 붙이는 값. 키워드 알림은 종류를 붙이지 않는다(예전 주소와 같다).
const ID_PARAM = 'notificationId';
const TYPE_PARAM = 'notificationType';

/** 푸시 알림을 눌러 들어오면 주소의 알림으로 확인·읽음 처리하고 주소에서 지운다. */
export function useNotificationOpenedFromUrl() {
    const [searchParams, setSearchParams] = useSearchParams();

    useEffect(() => {
        if (!searchParams.has(ID_PARAM)) return;
        const id = Number(searchParams.get(ID_PARAM));
        const type = searchParams.has(TYPE_PARAM) ? notificationTypeOf(searchParams.get(TYPE_PARAM)) : 'KEYWORD';
        if (Number.isSafeInteger(id) && type !== 'UNKNOWN') {
            void notificationRepository.ack({ type, id }).catch(() => undefined);
            void readNotification({ type, id });
        }
        const next = new URLSearchParams(searchParams);
        next.delete(ID_PARAM);
        next.delete(TYPE_PARAM);
        setSearchParams(next, { replace: true });
    }, [searchParams, setSearchParams]);
}
