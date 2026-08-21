export interface LoginRequest {
  phoneNumber: string;
  password: string;
}

export interface AuthMemberResponse {
  phoneNumber: string;
}

export interface SellerIdentity {
  phoneNumber: string;
}

export interface SellerSummary {
  listingCount: number;
  messageCount: number;
}

export interface ListingSummary {
  id: number;
  title: string;
  thumbnailUrl: string;
  pickupTimeText: string;
  canHelpMove: boolean;
  createdAt: string;
}

export interface SellerListingSummary extends ListingSummary {
  messageCount: number;
  latestMessageAt?: string | null;
}

export interface SellerPageResponse {
  seller: SellerIdentity;
  summary: SellerSummary;
  listings: SellerListingSummary[];
}

export interface ListingListResponse {
  items: ListingSummary[];
}

export interface ListingImage {
  id: number;
  url: string;
  displayOrder: number;
}

export interface ListingDetailResponse {
  id: number;
  title: string;
  description: string;
  pickupTimeText: string;
  canHelpMove: boolean;
  images: ListingImage[];
  createdAt: string;
  updatedAt: string;
}

export interface ListingDraft {
  title: string;
  description: string;
  pickupTimeText: string;
  canHelpMove: boolean;
}

/** multipart의 `request` 파트에 들어가는 JSON 계약이다. */
export type CreateListingRequest = ListingDraft;

/** 화면에서 등록 API에 넘기는 JSON 필드와 파일을 한 객체로 묶은 입력이다. */
export interface CreateListingInput extends ListingDraft {
  images: readonly File[];
}

export interface CreateListingResponse {
  listingId: number;
  createdAt: string;
}

type RequireAtLeastOne<T> = {
  [Key in keyof T]-?: Pick<T, Key> & Partial<Omit<T, Key>>;
}[keyof T];

/** PATCH 요청이 빈 객체가 되지 않도록 적어도 한 필드를 요구한다. */
export type UpdateListingRequest = RequireAtLeastOne<ListingDraft>;

export interface CreateMessageRequest {
  content: string;
}

export interface CreateMessageResponse {
  messageId: number;
  createdAt: string;
}

export interface ListingMessage {
  id: number;
  content: string;
  createdAt: string;
}

/** 화면 코드에서 간결한 이름이 필요할 때 사용할 수 있는 동일 타입 별칭이다. */
export type Message = ListingMessage;

export interface MessageListResponse {
  listingId: number;
  items: ListingMessage[];
}

export type MarketplaceErrorCode =
  | 'INVALID_REQUEST'
  | 'INVALID_IMAGE_COUNT'
  | 'INVALID_CREDENTIALS'
  | 'AUTHENTICATION_REQUIRED'
  | 'LISTING_ACCESS_DENIED'
  | 'LISTING_NOT_FOUND'
  | 'PHONE_ALREADY_REGISTERED'
  | 'PAYLOAD_TOO_LARGE'
  | 'UNSUPPORTED_IMAGE_TYPE'
  | 'INTERNAL_SERVER_ERROR';

export interface MarketplaceErrorResponse {
  code?: MarketplaceErrorCode | string;
  message?: string;
  fieldErrors?: Record<string, string>;
}
