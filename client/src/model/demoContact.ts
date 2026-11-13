/** 부스 시연에서 가입 화면의 거래 정보를 대신 채울 값. */
export type DemoContact = {
    // 하이픈 없는 숫자만 담는다
    phoneNumber: string;
    address: string;
    dongCode: string;
};

type DemoPlace = { address: string; dongCode: string };

/**
 * 묶음 배송 시연 지도(상도동 일대 250m 격자 8×5칸) 안의 칸마다 한 곳씩 고른 상가·공공 건물 주소와 그 행정동.
 * prod 주소 기준 데이터(상도동)로 해석돼야 주문에 출발·도착 칸이 생기고, 여러 사람이 가입해도 칸이 흩어진다.
 * 주소와 동을 함께 골라야 배송비 시연에서 주소와 동네가 어긋나지 않는다.
 */
export const DEMO_PLACES: readonly DemoPlace[] = [
    { address: '서울특별시 동작구 상도로15길 83', dongCode: '1159054000' }, // 상도2동
    { address: '서울특별시 동작구 등용로 2', dongCode: '1159054000' }, // 상도2동
    { address: '서울특별시 동작구 장승배기로10길 17', dongCode: '1159054000' }, // 상도2동
    { address: '서울특별시 동작구 상도로 222', dongCode: '1159054000' }, // 상도2동
    { address: '서울특별시 동작구 상도로 254', dongCode: '1159054000' }, // 상도2동
    { address: '서울특별시 동작구 대방동6길 19', dongCode: '1159055000' }, // 상도3동
    { address: '서울특별시 동작구 상도로15가길 16', dongCode: '1159055000' }, // 상도3동
    { address: '서울특별시 동작구 상도로 136', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 상도로22길 36', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 장승배기로 45', dongCode: '1159054000' }, // 상도2동
    { address: '서울특별시 동작구 양녕로36길 15', dongCode: '1159053000' }, // 상도1동
    { address: '서울특별시 동작구 상도로 102', dongCode: '1159055000' }, // 상도3동
    { address: '서울특별시 동작구 성대로29길 57', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 성대로31길 2', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 장승배기로 14', dongCode: '1159054000' }, // 상도2동
    { address: '서울특별시 동작구 양녕로30길 19-4', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 국사봉길 33', dongCode: '1159055000' }, // 상도3동
    { address: '서울특별시 동작구 국사봉1길 87', dongCode: '1159055000' }, // 상도3동
    { address: '서울특별시 동작구 성대로10가길 5', dongCode: '1159055000' }, // 상도3동
    { address: '서울특별시 동작구 성대로16길 21', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 성대로 164', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 양녕로 182', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 성대로6가길 100', dongCode: '1159055000' }, // 상도3동
    { address: '서울특별시 동작구 성대로12길 71', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 양녕로23길 27', dongCode: '1159056000' }, // 상도4동
    { address: '서울특별시 동작구 양녕로 156', dongCode: '1159056000' } // 상도4동
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
