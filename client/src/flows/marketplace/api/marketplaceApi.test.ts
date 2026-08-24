import { beforeEach, expect, jest, test } from '@jest/globals';
import {
  MarketplaceApiError,
  createListing,
  getListings,
  login,
  logout,
} from './marketplaceApi';

const fetchMock = jest.fn<typeof fetch>();

function response(body?: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

beforeEach(() => {
  fetchMock.mockReset();
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    value: fetchMock,
    writable: true,
  });
});

test('로그인은 휴대폰 번호를 정규화하고 세션 쿠키를 포함해 JSON으로 보낸다', async () => {
  fetchMock.mockResolvedValueOnce(response({ phoneNumber: '01012345678' }));

  await login({ phoneNumber: '010-1234-5678', password: '1234' });

  const [url, init] = fetchMock.mock.calls[0] ?? [];
  expect(String(url)).toMatch(/\/api\/auth\/login$/);
  expect(init?.credentials).toBe('include');
  expect(init?.method).toBe('POST');
  expect((init?.headers as Headers).get('Content-Type')).toBe('application/json');
  expect(JSON.parse(String(init?.body))).toEqual({
    phoneNumber: '01012345678',
    password: '1234',
  });
});

test('매물 목록 응답의 items만 화면에 반환한다', async () => {
  const items = [
    {
      id: 1,
      title: '테스트 의자',
      thumbnailUrl: 'https://example.com/test-chair.jpg',
      price: 30000,
      pickupTimeText: '주말 가능',
      canHelpMove: false,
      createdAt: '2026-08-21T13:00:00+09:00',
    },
  ];
  fetchMock.mockResolvedValueOnce(response({ items }));

  await expect(getListings()).resolves.toEqual(items);
});

test('매물 등록 multipart에는 Content-Type을 직접 지정하지 않는다', async () => {
  fetchMock.mockResolvedValueOnce(
    response({ listingId: 1, createdAt: '2026-08-21T13:00:00+09:00' }, 201),
  );
  const photo = new File(['image'], 'test-chair.webp', { type: 'image/webp' });

  await createListing({
    title: '테스트 의자',
    description: '가상 테스트 매물입니다.',
    price: 30000,
    pickupTimeText: '주말 가능',
    canHelpMove: true,
    images: [photo],
  });

  const init = fetchMock.mock.calls[0]?.[1];
  const headers = init?.headers as Headers;
  const formData = init?.body as FormData;
  expect(init?.credentials).toBe('include');
  expect(headers.has('Content-Type')).toBe(false);
  expect(formData.get('request')).toBeInstanceOf(Blob);
  expect(formData.getAll('images')).toEqual([photo]);
});

test('204 응답과 서버 오류 코드를 구분한다', async () => {
  fetchMock.mockResolvedValueOnce(response(undefined, 204));
  await expect(logout()).resolves.toBeUndefined();

  fetchMock.mockResolvedValueOnce(
    response({ code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.' }, 401),
  );
  const error = await getListings().catch((reason: unknown) => reason);
  expect(error).toBeInstanceOf(MarketplaceApiError);
  expect(error).toMatchObject({
    status: 401,
    code: 'AUTHENTICATION_REQUIRED',
    message: '로그인이 필요합니다.',
  });
});
