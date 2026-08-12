import { dispatchClient } from './dispatchClient';
import type {
  BuyerDispatchRequestCreateRequest,
  BuyerDispatchRequestCreateResponse,
  BuyerDispatchRequestResponse,
  DispatchItemImageResponse,
  SellerInputSessionResponse,
  SellerInputSubmitRequest,
} from '../model/dispatchTypes';

const BUYER_PATH = '/api/dispatch-requests';
const ITEM_IMAGE_PATH = '/api/dispatch-requests/images';
const SELLER_SESSION_PATH = '/api/dispatch-requests/seller-sessions';

export const createBuyerDispatchRequest = (request: BuyerDispatchRequestCreateRequest) =>
  dispatchClient.post<BuyerDispatchRequestCreateResponse>(BUYER_PATH, request);

/**
 * 물품 사진 한 장을 먼저 올리고 URL을 받는다.
 * 배차 요청 생성은 이 URL만 `itemImageUrls`로 전달한다.
 */
export const uploadDispatchItemImage = (image: File) => {
  const formData = new FormData();
  formData.append('image', image);

  return dispatchClient.postForm<DispatchItemImageResponse>(ITEM_IMAGE_PATH, formData);
};

export const findBuyerDispatchRequest = (buyerToken: string) =>
  dispatchClient.get<BuyerDispatchRequestResponse>(
    `${BUYER_PATH}/${encodeURIComponent(buyerToken)}`,
  );

/** 구매자가 운영자의 최종 금액에 동의한다. 거절은 server 계약이 없다. */
export const approveFinalAmount = (buyerToken: string) =>
  dispatchClient.post<void>(`${BUYER_PATH}/${encodeURIComponent(buyerToken)}/approval`);

export const findSellerInputSession = (token: string) =>
  dispatchClient.get<SellerInputSessionResponse>(
    `${SELLER_SESSION_PATH}/${encodeURIComponent(token)}`,
  );

export const submitSellerInput = (token: string, request: SellerInputSubmitRequest) =>
  dispatchClient.post<void>(`${SELLER_SESSION_PATH}/${encodeURIComponent(token)}`, request);
