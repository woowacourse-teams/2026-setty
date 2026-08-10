/**
 * server의 `setty.dispatch` 계약을 그대로 옮긴 타입이다.
 * 계약에 없는 필드를 여기에서 추측해 추가하지 않는다.
 */

/** server `DispatchStatus` */
export type DispatchStatus =
  | 'SELLER_INPUT_PENDING'
  | 'FINAL_REVIEW_PENDING'
  | 'FINAL_AMOUNT_CONFIRM_PENDING'
  | 'DISPATCH_PENDING'
  | 'DISPATCH_COMPLETED'
  | 'IN_TRANSIT'
  | 'DELIVERY_COMPLETED'
  | 'FINAL_AMOUNT_REJECTED'
  | 'TRANSPORT_INFEASIBLE'
  | 'USER_CANCELLED'
  | 'DISPATCH_FAILED';

/** POST /api/dispatch-requests */
export interface BuyerDispatchRequestCreateRequest {
  buyerName: string;
  buyerPhoneNumber: string;
  deliveryAddress: string;
  itemType: string;
  highValueItem: boolean;
  /** 당근 게시물 링크. server `@NotBlank`라 배차 요청에서는 필수다. */
  productLink: string;
  estimateRequestId?: number | null;
}

export interface BuyerDispatchRequestCreateResponse {
  buyerToken: string;
  sellerInputUrl: string;
}

/** GET /api/dispatch-requests/{buyerToken} */
export interface BuyerDispatchRequestResponse {
  status: DispatchStatus;
  buyerName: string;
  buyerPhoneNumber: string;
  deliveryAddress: string;
  itemType: string;
  highValueItem: boolean;
  sellerInputCompleted: boolean;
  createdAt: string;
  sellerInputUrl: string;
  /** 운영자가 기록하기 전에는 `null`이다. */
  finalQuotedAmount: number | null;
}

/** GET /api/dispatch-requests/seller-sessions/{token} */
export interface SellerInputSessionResponse {
  itemType: string;
  alreadySubmitted: boolean;
}

/** POST /api/dispatch-requests/seller-sessions/{token} */
export interface SellerInputSubmitRequest {
  sellerName: string;
  sellerPhoneNumber: string;
  pickupAddress: string;
  availablePickupTime: string;
}
