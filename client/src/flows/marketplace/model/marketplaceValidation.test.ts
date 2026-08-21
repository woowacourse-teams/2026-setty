import { expect, test } from '@jest/globals';
import {
  MAX_LISTING_IMAGE_TOTAL_BYTES,
  normalizePhoneNumber,
  validateListingImages,
  validateLoginRequest,
  validateMessageRequest,
} from './marketplaceValidation';

const image = (type: string, size = 100): File => ({ type, size }) as File;

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

test('익명 쪽지는 공백을 제외하고 1~500자를 요구한다', () => {
  expect(validateMessageRequest({ content: '   ' })).toEqual({
    content: '쪽지를 1~500자로 입력해 주세요.',
  });
  expect(validateMessageRequest({ content: '아직 판매 중인가요?' })).toEqual({});
});
