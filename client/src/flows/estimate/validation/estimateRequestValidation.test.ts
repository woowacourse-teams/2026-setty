import { expect, test } from '@jest/globals';
import {
  EstimateRequestFormValues,
  MAX_PRODUCT_LINK,
  validateEstimateRequest,
} from './estimateRequestValidation';

const VALID_VALUES: EstimateRequestFormValues = {
  name: '홍 길동',
  phoneNumber: '010-0000-0000',
  tradeArea: '테스트구 테스트동',
  itemType: '테스트 의자',
  highValueItem: false,
  productLink: '',
};

test('이름의 앞뒤 공백은 제거하고 내부 공백은 허용한다', () => {
  expect(
    validateEstimateRequest({ ...VALID_VALUES, name: '  홍 길동  ' }).name,
  ).toBeUndefined();
});

test('공백만 있는 이름은 거절한다', () => {
  expect(validateEstimateRequest({ ...VALID_VALUES, name: '   ' }).name).toBe(
    '이름을 입력해 주세요.',
  );
});

test('50만 원 초과 여부는 체크하지 않아도 유효한 값으로 본다', () => {
  expect(validateEstimateRequest({ ...VALID_VALUES, highValueItem: false })).toEqual({});
});

test('당근 게시물 링크는 비어 있어도 유효한 값으로 본다', () => {
  expect(validateEstimateRequest({ ...VALID_VALUES, productLink: '   ' })).toEqual({});
});

test('당근 게시물 링크가 500자를 넘으면 거절한다', () => {
  const tooLongLink = `https://www.daangn.com/articles/${'0'.repeat(MAX_PRODUCT_LINK)}`;

  expect(
    validateEstimateRequest({ ...VALID_VALUES, productLink: tooLongLink }).productLink,
  ).toBe('당근 게시물 링크는 500자 이하로 입력해 주세요.');
});
