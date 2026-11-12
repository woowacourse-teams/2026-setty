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

/** [min] 이상 [max] 이하의 정수. */
function between(min: number, max: number, random: () => number): number {
    return min + Math.min(Math.floor(random() * (max - min + 1)), max - min);
}

/** 하이픈 없는 010-0000-XXXX 번호. */
export function randomDemoPhoneNumber(random: () => number = Math.random): string {
    return DEMO_PHONE_PREFIX + String(between(1000, 9999, random));
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
    return { phoneNumber: randomDemoPhoneNumber(random), address: place.address, dongCode: place.dongCode };
}

/** 부스 시연에서 배송원 가입 화면의 배송 정보를 대신 채울 값. */
export type DemoDriver = {
    // 하이픈 없는 숫자만 담는다
    phoneNumber: string;
    licensePlateNumber: string;
    carType: string;
    businessRegistrationNumber: string;
};

export const DEMO_CAR_TYPES: readonly string[] = ['1톤 탑차', '1톤 카고', '1.4톤 탑차', '2.5톤 탑차'];

/** 세무서 코드 000은 없으므로 실제 사업자등록번호와 겹치지 않는다. */
export const DEMO_BUSINESS_NUMBER_PREFIX = '000-00-';

/** 시연용 배송원 전화번호·번호판·차종·사업자등록번호를 고른다. 번호판은 택배 영업용 '배' 글자를 쓴다. */
export function randomDemoDriver(random: () => number = Math.random): DemoDriver {
    return {
        phoneNumber: randomDemoPhoneNumber(random),
        licensePlateNumber: `${between(80, 99, random)}배${between(1000, 9999, random)}`,
        carType: pick(DEMO_CAR_TYPES, random),
        businessRegistrationNumber: DEMO_BUSINESS_NUMBER_PREFIX + String(between(0, 99999, random)).padStart(5, '0')
    };
}
