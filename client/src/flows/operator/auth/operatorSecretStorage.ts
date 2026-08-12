const OPERATOR_SECRET_STORAGE_KEY = 'setty.operatorSecret';

export function storeOperatorSecret(secret: string): void {
  sessionStorage.setItem(OPERATOR_SECRET_STORAGE_KEY, secret);
}

export function getOperatorSecret(): string | null {
  try {
    return sessionStorage.getItem(OPERATOR_SECRET_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function hasOperatorSecret(): boolean {
  return Boolean(getOperatorSecret());
}

export function clearOperatorSecret(): void {
  try {
    sessionStorage.removeItem(OPERATOR_SECRET_STORAGE_KEY);
  } catch {
    // 저장소 접근이 제한돼도 로그아웃 화면 이동은 계속 진행한다.
  }
}
