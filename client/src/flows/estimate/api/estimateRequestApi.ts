import { requestJson } from '@/shared/api/http';

export interface CreateEstimateRequestPayload {
  name: string;
  phoneNumber: string;
  tradeArea: string;
  itemType: string;
  highValueItem: boolean;
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
