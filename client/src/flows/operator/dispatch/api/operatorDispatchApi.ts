import { requestOperatorJson } from '@/flows/operator/auth/operatorAuthApi';
import { ApiError } from '@/shared/api/http';

export const DISPATCH_STATUSES = [
  'SELLER_INPUT_PENDING',
  'FINAL_REVIEW_PENDING',
  'FINAL_AMOUNT_CONFIRM_PENDING',
  'DISPATCH_PENDING',
  'DISPATCH_COMPLETED',
  'IN_TRANSIT',
  'DELIVERY_COMPLETED',
  'FINAL_AMOUNT_REJECTED',
  'TRANSPORT_INFEASIBLE',
  'USER_CANCELLED',
  'DISPATCH_FAILED',
] as const;

export type DispatchStatus = (typeof DISPATCH_STATUSES)[number];

export interface OperatorDispatchRequestSummary {
  id: number;
  status: DispatchStatus;
  itemType: string;
  highValueItem: boolean;
  sellerInputCompleted: boolean;
  finalQuotedAmount: number | null;
  createdAt: string;
}

export interface OperatorDispatchBuyer {
  name: string;
  phoneNumber: string;
  deliveryAddress: string;
}

export interface OperatorDispatchSeller {
  name: string;
  phoneNumber: string;
  pickupAddress: string;
  availablePickupTime: string;
}

export interface OperatorDispatchRequestDetail extends Omit<
  OperatorDispatchRequestSummary,
  'sellerInputCompleted'
> {
  productLink: string | null;
  itemImageUrls: string[];
  estimateRequestId: number | null;
  buyer: OperatorDispatchBuyer;
  seller: OperatorDispatchSeller | null;
  sellerInputUrl: string | null;
  sellerInputCompletedAt: string | null;
  messageContent: string | null;
  buyerConfirmUrl: string | null;
  amountCheckedAt: string | null;
  operatorNote: string | null;
  closedReason: string | null;
}

export interface SaveFinalAmountPayload {
  finalQuotedAmount: number;
}

export interface SaveFinalAmountResponse {
  buyerConfirmUrl: string;
}

export interface SaveMessagePayload {
  messageContent: string;
}

export function isDispatchStatus(value: string | null): value is DispatchStatus {
  return DISPATCH_STATUSES.some((status) => status === value);
}

export function getOperatorDispatchRequests(
  status?: DispatchStatus,
  signal?: AbortSignal,
): Promise<OperatorDispatchRequestSummary[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return requestOperatorJson<OperatorDispatchRequestSummary[]>(
    `/api/operator/dispatch-requests${query}`,
    { signal },
  );
}

export function getOperatorDispatchRequest(
  dispatchRequestId: string,
  signal?: AbortSignal,
): Promise<OperatorDispatchRequestDetail> {
  return requestOperatorJson<OperatorDispatchRequestDetail>(
    `/api/operator/dispatch-requests/${encodeURIComponent(dispatchRequestId)}`,
    { signal },
  );
}

function hasKnownFinalAmountFieldError(
  fieldErrors: Record<string, string> | undefined,
): boolean {
  return Boolean(fieldErrors?.finalQuotedAmount);
}

export async function saveFinalAmount(
  dispatchRequestId: string,
  payload: SaveFinalAmountPayload,
): Promise<SaveFinalAmountResponse> {
  try {
    return await requestOperatorJson<SaveFinalAmountResponse>(
      `/api/operator/dispatch-requests/${encodeURIComponent(dispatchRequestId)}/final-amount`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
    );
  } catch (error) {
    if (
      error instanceof ApiError &&
      error.status === 400 &&
      !hasKnownFinalAmountFieldError(error.fieldErrors)
    ) {
      throw new ApiError(400, { code: error.code, message: error.message });
    }
    throw error;
  }
}

export async function saveMessage(
  dispatchRequestId: string,
  payload: SaveMessagePayload,
): Promise<void> {
  try {
    await requestOperatorJson<void>(
      `/api/operator/dispatch-requests/${encodeURIComponent(dispatchRequestId)}/message`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
    );
  } catch (error) {
    if (
      error instanceof ApiError &&
      error.status === 400 &&
      !error.fieldErrors?.messageContent
    ) {
      throw new ApiError(400, { code: error.code, message: error.message });
    }
    throw error;
  }
}

export function completeDispatch(dispatchRequestId: string): Promise<void> {
  return requestOperatorJson<void>(
    `/api/operator/dispatch-requests/${encodeURIComponent(dispatchRequestId)}/completion`,
    { method: 'POST' },
  );
}
