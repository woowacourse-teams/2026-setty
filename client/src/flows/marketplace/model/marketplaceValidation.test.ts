import { expect, test } from '@jest/globals';
import {
  MAX_LISTING_IMAGE_TOTAL_BYTES,
  MAX_LISTING_PRICE,
  normalizePhoneNumber,
  validateListingDraft,
  validateListingImages,
  validateLoginRequest,
  validateMessageRequest,
} from './marketplaceValidation';
import type { ListingDraft } from './marketplaceTypes';

const image = (type: string, size = 100): File => ({ type, size }) as File;

const draft = (price: number): ListingDraft => ({
  title: '가상 테스트 협탁',
  description: '테스트 전용 매물 설명입니다.',
  price,
  pickupTimeText: '토요일 오전',
  canHelpMove: false,
});

test('휴대폰 번호를 숫자로 정규화하고 숫자 4자리 비밀번호만 허용한다', () => {
  expect(normalizePhoneNumber('010-1234-5678')).toBe('01012345678');
  expect(
    validateLoginRequest({ phoneNumber: '010-1234-5678', password: '1234' }),
  ).toEqual({});
  expect(validateLoginRequest({ phoneNumber: '010-123-456', password: '12345' })).toEqual(
    {
      phoneNumber: '휴대폰 번호를 숫자 10~11자리로 입력해 주세요.',
      password: '비밀번호를 숫자 4자리로 입력해 주세요.',
    },
  );
});

test('등록 이미지는 1~5장, 허용 형식, 전체 25MB 규칙을 적용한다', () => {
  expect(validateListingImages([])).toBe('사진을 1~5장 선택해 주세요.');
  expect(validateListingImages([image('image/gif')])).toBe(
    'JPEG, PNG, WebP 사진만 올릴 수 있어요.',
  );
  expect(
    validateListingImages([
      image('image/jpeg', MAX_LISTING_IMAGE_TOTAL_BYTES),
      image('image/png', 1),
    ]),
  ).toBe('사진 전체 용량은 25MB 이하여야 해요.');
  expect(validateListingImages([image('image/webp')])).toBeUndefined();
});

test('매물 가격은 0 이상 정수만 허용하고 미입력·음수·상한 초과를 막는다', () => {
  const priceError = '가격을 0원 이상 숫자로 입력해 주세요.';
  expect(validateListingDraft(draft(30000)).price).toBeUndefined();
  expect(validateListingDraft(draft(0)).price).toBeUndefined();
  expect(validateListingDraft(draft(Number.NaN)).price).toBe(priceError);
  expect(validateListingDraft(draft(-1)).price).toBe(priceError);
  expect(validateListingDraft(draft(MAX_LISTING_PRICE + 1)).price).toBe(priceError);
});

test('익명 쪽지는 공백을 제외하고 1~500자를 요구한다', () => {
  expect(validateMessageRequest({ content: '   ' })).toEqual({
    content: '쪽지를 1~500자로 입력해 주세요.',
  });
  expect(validateMessageRequest({ content: '아직 판매 중인가요?' })).toEqual({});
});
