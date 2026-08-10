import { requestJson } from '@/shared/api/http';

export interface CreateEstimateRequestPayload {
  name: string;
  phoneNumber: string;
  tradeArea: string;
  itemType: string;
  highValueItem: boolean;
  /** 당근 게시물 링크. 선택 입력이라 비어 있으면 보내지 않는다. */
  productLink?: string;
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
