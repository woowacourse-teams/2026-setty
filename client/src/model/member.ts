export type Member = {
    id: number;
    loginId: string;
    phoneNumber: string;
    address: string;
    dongCode: string | null;
};

const PHONE_NUMBER_REGEX = /^010\d{8}$/;
export const PHONE_NUMBER_MAX_DIGITS = 11;
export const ADDRESS_MAX_LENGTH = 200;

export function digitsOnly(value: string, maxLength: number): string {
    return value.replace(/\D/g, '').slice(0, maxLength);
}

export function isPhoneNumberValid(digits: string): boolean {
    return PHONE_NUMBER_REGEX.test(digits);
}

/** 전화번호를 010-0000-0000 형식으로 바꾼다. 서버로 보낼 값과 입력 칸에 보여줄 값 모두 이 형식이다. */
export function formatPhoneNumber(value: string): string {
    const digits = digitsOnly(value, PHONE_NUMBER_MAX_DIGITS);
    if (digits.length <= 3) return digits;
    if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
    return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}
