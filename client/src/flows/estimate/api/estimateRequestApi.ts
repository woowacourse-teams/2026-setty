import { requestJson } from '@/shared/api/http';

export interface CreateEstimateRequestPayload {
  name: string;
  phoneNumber: string;
  tradeArea: string;
  itemType: string;
  highValueItem: boolean;
  /** 당근 게시물 링크. 선택 입력이라 비어 있으면 보내지 않는다. */
  productLink?: string;
  /** 필수 동의를 체크하고 제출했는지. server는 true일 때만 동의 시각을 남긴다. */
  privacyConsent: boolean;
  /** 화면이 실제로 보여준 안내문 버전. server `@Size(max = 20)`이다. */
  privacyPolicyVersion: string;
}

export interface CreateEstimateRequestResponse {
  estimateRequestId: number;
  status: 'PENDING_REVIEW';
  createdAt: string;
}

export function createEstimateRequest(
  payload: CreateEstimateRequestPayload,
): Promise<CreateEstimateRequestResponse> {
  return requestJson<CreateEstimateRequestResponse>('/api/estimate-requests', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
