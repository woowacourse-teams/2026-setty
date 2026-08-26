import { SignupRequest } from '@/model/auth';

/**
 * 회원가입 클라이언트 1차 검증. 서버 규칙(SignupRequest)과 동일하게 맞춘다.
 * 최종 검증은 서버가 하며, 서버 INVALID_REQUEST 문구는 화면에서 별도로 보여준다.
 */
const RULES = {
  loginId: /^[a-z0-9]{4,20}$/,
  phoneNumber: /^010-\d{4}-\d{4}$/,
  licensePlateNumber: /^\d{2,3}[가-힣]\d{4}$/,
  businessRegistrationNumber: /^\d{3}-\d{2}-\d{5}$/,
} as const;

export type SignupErrors = Partial<Record<keyof SignupRequest, string>>;

export function validateSignup(v: SignupRequest): SignupErrors {
  const e: SignupErrors = {};
  if (!RULES.loginId.test(v.loginId)) e.loginId = '영문 소문자·숫자 4~20자';
  if (v.password.length < 8 || v.password.length > 64) e.password = '8~64자';
  if (!RULES.phoneNumber.test(v.phoneNumber)) e.phoneNumber = '010-0000-0000 형식';
  if (!RULES.licensePlateNumber.test(v.licensePlateNumber)) {
    e.licensePlateNumber = '00가0000 형식';
  }
  if (v.carType.trim().length < 1 || v.carType.length > 30) e.carType = '1~30자';
  if (!RULES.businessRegistrationNumber.test(v.businessRegistrationNumber)) {
    e.businessRegistrationNumber = '000-00-00000 형식';
  }
  return e;
}

/** 로그인 최소 검증. 통과하면 null. */
export function validateLogin(loginId: string, password: string): string | null {
  if (!loginId.trim() || !password) return '아이디와 비밀번호를 입력해 주세요';
  return null;
}
