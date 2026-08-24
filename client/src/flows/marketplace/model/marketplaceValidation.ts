import type {
  CreateListingInput,
  CreateMessageRequest,
  ListingDraft,
  LoginRequest,
} from './marketplaceTypes';

export const MAX_LISTING_IMAGE_COUNT = 5;
export const MAX_LISTING_IMAGE_TOTAL_BYTES = 25 * 1024 * 1024;
export const SUPPORTED_LISTING_IMAGE_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
] as const;

const NORMALIZED_PHONE_NUMBER_PATTERN = /^\d{10,11}$/;
const MARKETPLACE_PASSWORD_PATTERN = /^\d{4}$/;

export interface LoginValidationErrors {
  phoneNumber?: string;
  password?: string;
}

export interface ListingValidationErrors {
  title?: string;
  price?: string;
  description?: string;
  pickupTimeText?: string;
  images?: string;
}

export const MAX_LISTING_PRICE = 100_000_000;

export interface MessageValidationErrors {
  content?: string;
}

export function normalizePhoneNumber(value: string): string {
  return value.replace(/\D/g, '');
}

export function isValidPhoneNumber(value: string): boolean {
  return NORMALIZED_PHONE_NUMBER_PATTERN.test(normalizePhoneNumber(value));
}

export function isValidMarketplacePassword(value: string): boolean {
  return MARKETPLACE_PASSWORD_PATTERN.test(value);
}

export function validateLoginRequest(request: LoginRequest): LoginValidationErrors {
  const errors: LoginValidationErrors = {};

  if (!isValidPhoneNumber(request.phoneNumber)) {
    errors.phoneNumber = '휴대폰 번호를 숫자 10~11자리로 입력해 주세요.';
  }

  if (!isValidMarketplacePassword(request.password)) {
    errors.password = '비밀번호를 숫자 4자리로 입력해 주세요.';
  }

  return errors;
}

export function validateListingDraft(draft: ListingDraft): ListingValidationErrors {
  const errors: ListingValidationErrors = {};
  const titleLength = draft.title.trim().length;
  const descriptionLength = draft.description.trim().length;
  const pickupTimeLength = draft.pickupTimeText.trim().length;

  if (titleLength < 1 || titleLength > 100) {
    errors.title = '제목을 1~100자로 입력해 주세요.';
  }

  if (!Number.isInteger(draft.price) || draft.price < 0 || draft.price > MAX_LISTING_PRICE) {
    errors.price = '가격을 0원 이상 숫자로 입력해 주세요.';
  }

  if (descriptionLength < 1 || descriptionLength > 500) {
    errors.description = '설명을 1~500자로 입력해 주세요.';
  }

  if (pickupTimeLength < 1 || pickupTimeLength > 50) {
    errors.pickupTimeText = '픽업 가능 시간을 1~50자로 입력해 주세요.';
  }

  return errors;
}

export function validateListingImages(images: readonly File[]): string | undefined {
  if (images.length < 1 || images.length > MAX_LISTING_IMAGE_COUNT) {
    return '사진을 1~5장 선택해 주세요.';
  }

  if (
    images.some(
      (image) =>
        !SUPPORTED_LISTING_IMAGE_TYPES.includes(
          image.type as (typeof SUPPORTED_LISTING_IMAGE_TYPES)[number],
        ),
    )
  ) {
    return 'JPEG, PNG, WebP 사진만 올릴 수 있어요.';
  }

  const totalBytes = images.reduce((sum, image) => sum + image.size, 0);
  if (totalBytes > MAX_LISTING_IMAGE_TOTAL_BYTES) {
    return '사진 전체 용량은 25MB 이하여야 해요.';
  }

  return undefined;
}

export function validateCreateListingInput(
  input: CreateListingInput,
): ListingValidationErrors {
  const errors = validateListingDraft(input);
  const imagesError = validateListingImages(input.images);

  if (imagesError) {
    errors.images = imagesError;
  }

  return errors;
}

export function validateMessageRequest(
  request: CreateMessageRequest,
): MessageValidationErrors {
  const contentLength = request.content.trim().length;
  if (contentLength < 1 || contentLength > 500) {
    return { content: '쪽지를 1~500자로 입력해 주세요.' };
  }

  return {};
}
