import { marketplaceRequest } from './marketplaceClient';
import type { MarketplaceRequestOptions } from './marketplaceClient';
import { normalizePhoneNumber } from '../model/marketplaceValidation';
import type {
  AuthMemberResponse,
  CreateListingInput,
  CreateListingRequest,
  CreateListingResponse,
  CreateMessageRequest,
  CreateMessageResponse,
  ListingDetailResponse,
  ListingListResponse,
  ListingSummary,
  LoginRequest,
  MessageListResponse,
  SellerPageResponse,
  UpdateListingRequest,
} from '../model/marketplaceTypes';

const AUTH_PATH = '/api/auth';
const LISTINGS_PATH = '/api/listings';

function listingPath(listingId: number): string {
  return `${LISTINGS_PATH}/${encodeURIComponent(String(listingId))}`;
}

/**
 * 확정된 프로토타입 정책에 따라 미등록 번호의 계정 생성과 기존 회원 로그인을
 * 동일한 `/api/auth/login` 요청으로 처리한다.
 */
export function login(
  request: LoginRequest,
  options?: MarketplaceRequestOptions,
): Promise<AuthMemberResponse> {
  return marketplaceRequest<AuthMemberResponse>(`${AUTH_PATH}/login`, {
    method: 'POST',
    body: {
      ...request,
      phoneNumber: normalizePhoneNumber(request.phoneNumber),
    },
    signal: options?.signal,
  });
}

export const loginOrCreateAccount = login;

export function logout(options?: MarketplaceRequestOptions): Promise<void> {
  return marketplaceRequest<void>(`${AUTH_PATH}/logout`, {
    method: 'POST',
    signal: options?.signal,
  });
}

export function getSellerPage(
  options?: MarketplaceRequestOptions,
): Promise<SellerPageResponse> {
  return marketplaceRequest<SellerPageResponse>('/api/me/seller-page', {
    signal: options?.signal,
  });
}

export async function getListings(
  options?: MarketplaceRequestOptions,
): Promise<ListingSummary[]> {
  const response = await marketplaceRequest<ListingListResponse>(LISTINGS_PATH, {
    signal: options?.signal,
  });
  return response.items;
}

export function getListingDetail(
  listingId: number,
  options?: MarketplaceRequestOptions,
): Promise<ListingDetailResponse> {
  return marketplaceRequest<ListingDetailResponse>(listingPath(listingId), {
    signal: options?.signal,
  });
}

export const getListing = getListingDetail;

export function createListing(
  input: CreateListingInput,
  options?: MarketplaceRequestOptions,
): Promise<CreateListingResponse> {
  const request: CreateListingRequest = {
    title: input.title,
    description: input.description,
    pickupTimeText: input.pickupTimeText,
    canHelpMove: input.canHelpMove,
  };
  const formData = new FormData();
  formData.append(
    'request',
    new Blob([JSON.stringify(request)], { type: 'application/json' }),
  );
  input.images.forEach((image) => formData.append('images', image));

  return marketplaceRequest<CreateListingResponse>(LISTINGS_PATH, {
    method: 'POST',
    body: formData,
    signal: options?.signal,
  });
}

export function updateListing(
  listingId: number,
  request: UpdateListingRequest,
  options?: MarketplaceRequestOptions,
): Promise<void> {
  return marketplaceRequest<void>(listingPath(listingId), {
    method: 'PATCH',
    body: request,
    signal: options?.signal,
  });
}

export function deleteListing(
  listingId: number,
  options?: MarketplaceRequestOptions,
): Promise<void> {
  return marketplaceRequest<void>(listingPath(listingId), {
    method: 'DELETE',
    signal: options?.signal,
  });
}

export function sendListingMessage(
  listingId: number,
  request: CreateMessageRequest,
  options?: MarketplaceRequestOptions,
): Promise<CreateMessageResponse> {
  return marketplaceRequest<CreateMessageResponse>(`${listingPath(listingId)}/messages`, {
    method: 'POST',
    body: request,
    signal: options?.signal,
  });
}

export function getListingMessages(
  listingId: number,
  options?: MarketplaceRequestOptions,
): Promise<MessageListResponse> {
  return marketplaceRequest<MessageListResponse>(`${listingPath(listingId)}/messages`, {
    signal: options?.signal,
  });
}

export type { MarketplaceRequestOptions } from './marketplaceClient';
export { MarketplaceApiError } from './marketplaceClient';
export type {
  AuthMemberResponse,
  CreateListingInput,
  CreateListingRequest,
  CreateListingResponse,
  CreateMessageRequest,
  CreateMessageResponse,
  ListingDetailResponse,
  ListingDraft,
  ListingImage,
  ListingListResponse,
  ListingMessage,
  ListingSummary,
  LoginRequest,
  MarketplaceErrorCode,
  MarketplaceErrorResponse,
  Message,
  MessageListResponse,
  SellerIdentity,
  SellerListingSummary,
  SellerPageResponse,
  SellerSummary,
  UpdateListingRequest,
} from '../model/marketplaceTypes';
