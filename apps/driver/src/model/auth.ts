/**
 * 배송원 인증 계약. (서버: setty.delivery.auth.controller.dto)
 * 필드명은 서버 DTO 그대로 유지한다.
 */

/** POST /api/delivery/auth/signup 요청 바디 */
export interface SignupRequest {
  loginId: string;
  password: string;
  phoneNumber: string;
  licensePlateNumber: string;
  carType: string;
  businessRegistrationNumber: string;
}

/** POST /api/delivery/auth/signup 성공 응답(201) */
export interface SignupResponse {
  id: number;
  loginId: string;
}

/** POST /api/delivery/auth/login 요청 바디 */
export interface LoginRequest {
  loginId: string;
  password: string;
}

/** POST /api/delivery/auth/login 성공 응답(200). 로그인마다 회전하는 UUID 토큰. */
export interface LoginResponse {
  token: string;
}
