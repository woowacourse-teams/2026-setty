export interface EstimateRequestFormValues {
  name: string;
  phoneNumber: string;
  tradeArea: string;
  itemType: string;
  /** server `highValueItem` — 체크하지 않으면 50만 원 초과가 아니라는 뜻이다. */
  highValueItem: boolean;
}

export type EstimateRequestField = keyof EstimateRequestFormValues;
export type EstimateRequestFieldErrors = Partial<Record<EstimateRequestField, string>>;

export function normalizePhoneNumber(phoneNumber: string): string {
  return phoneNumber.replace(/[\s-]/g, '');
}

export function formatPhoneNumber(phoneNumber: string): string {
  const digits = normalizePhoneNumber(phoneNumber);
  if (!/^010\d{8}$/.test(digits)) {
    return phoneNumber;
  }

  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

export function validateEstimateRequest(
  values: EstimateRequestFormValues,
): EstimateRequestFieldErrors {
  const errors: EstimateRequestFieldErrors = {};
  const name = values.name.trim();
  const tradeArea = values.tradeArea.trim();
  const itemType = values.itemType.trim();

  if (!name) {
    errors.name = '이름을 입력해 주세요.';
  } else if (name.length > 10) {
    errors.name = '이름은 10자 이하로 입력해 주세요.';
  }

  if (!/^010\d{8}$/.test(normalizePhoneNumber(values.phoneNumber))) {
    errors.phoneNumber = '010으로 시작하는 휴대전화 번호 11자리를 입력해 주세요.';
  }

  if (!tradeArea) {
    errors.tradeArea = '거래 지역을 입력해 주세요.';
  } else if (tradeArea.length > 100) {
    errors.tradeArea = '거래 지역은 100자 이하로 입력해 주세요.';
  }

  if (!itemType) {
    errors.itemType = '상품명을 입력해 주세요.';
  } else if (itemType.length > 100) {
    errors.itemType = '상품명은 100자 이하로 입력해 주세요.';
  }

  return errors;
}
