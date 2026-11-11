/** 부스 시연에서 가입 화면의 거래 정보를 대신 채울 값. */
export type DemoContact = {
    // 하이픈 없는 숫자만 담는다
    phoneNumber: string;
    address: string;
    dongCode: string;
};

type DemoPlace = { address: string; dongCode: string };

/**
 * 주소 기준 데이터로 해석되는 서울 도로명 주소와 그 주소의 행정동.
 * 주소와 동을 함께 골라야 배송비 시연에서 주소와 동네가 어긋나지 않는다.
 */
export const DEMO_PLACES: readonly DemoPlace[] = [
    { address: '서울특별시 중구 세종대로 110', dongCode: '1114052000' }, // 소공동
    { address: '서울특별시 종로구 종로 1', dongCode: '1111061500' }, // 종로1·2·3·4가동
    { address: '서울특별시 영등포구 의사당대로 1', dongCode: '1156054000' }, // 여의동
    { address: '서울특별시 강남구 영동대로 513', dongCode: '1168058000' }, // 삼성1동
    { address: '서울특별시 송파구 올림픽로 300', dongCode: '1171071000' }, // 잠실6동
    { address: '서울특별시 서대문구 연세로 50', dongCode: '1141058500' }, // 신촌동
    { address: '서울특별시 마포구 월드컵로 240', dongCode: '1144073000' }, // 성산2동
    { address: '서울특별시 광진구 능동로 120', dongCode: '1121571000' }, // 화양동
    { address: '서울특별시 동대문구 경희대로 26', dongCode: '1123071000' }, // 회기동
    { address: '서울특별시 성북구 안암로 145', dongCode: '1129060000' } // 안암동
];

/** 국번을 0000으로 둬 실제 가입자 번호와 겹치지 않게 하고, 화면에서 시연 계정인 것이 드러나게 한다. */
export const DEMO_PHONE_PREFIX = '0100000';

function pick<T>(items: readonly T[], random: () => number): T {
    return items[Math.min(Math.floor(random() * items.length), items.length - 1)];
}

/**
 * 시연용 전화번호·주소·행정동을 고른다. [isAvailableDong]이 받아 주는 동의 주소만 고르고,
 * 하나도 없으면 null을 돌려준다.
 */
export function randomDemoContact(
    isAvailableDong: (dongCode: string) => boolean,
    random: () => number = Math.random
): DemoContact | null {
    const places = DEMO_PLACES.filter((place) => isAvailableDong(place.dongCode));
    if (places.length === 0) return null;
    const place = pick(places, random);
    const lastFour = String(1000 + Math.min(Math.floor(random() * 9000), 8999));
    return { phoneNumber: DEMO_PHONE_PREFIX + lastFour, address: place.address, dongCode: place.dongCode };
}
