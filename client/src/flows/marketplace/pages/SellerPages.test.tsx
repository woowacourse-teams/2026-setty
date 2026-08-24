import { beforeAll, beforeEach, expect, jest, test } from '@jest/globals';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, type RouteObject, useRoutes } from 'react-router-dom';
import {
  createListing,
  deleteListing,
  getListingDetail,
  getListingMessages,
  getSellerPage,
  loginOrCreateAccount,
  MarketplaceApiError,
  updateListing,
} from '@/flows/marketplace/api/marketplaceApi';
import type {
  ListingDetailResponse,
  SellerPageResponse,
} from '@/flows/marketplace/model/marketplaceTypes';
import InboxPage from './InboxPage';
import ListingFormPage from './ListingFormPage';
import MinePage from './MinePage';

jest.mock('@/flows/marketplace/api/marketplaceApi', () => {
  const actual = jest.requireActual<
    typeof import('@/flows/marketplace/api/marketplaceApi')
  >('@/flows/marketplace/api/marketplaceApi');
  return {
    ...actual,
    createListing: jest.fn(),
    deleteListing: jest.fn(),
    getListingDetail: jest.fn(),
    getListingMessages: jest.fn(),
    getSellerPage: jest.fn(),
    loginOrCreateAccount: jest.fn(),
    logout: jest.fn(),
    updateListing: jest.fn(),
  };
});

const createListingMock = jest.mocked(createListing);
const deleteListingMock = jest.mocked(deleteListing);
const getListingDetailMock = jest.mocked(getListingDetail);
const getListingMessagesMock = jest.mocked(getListingMessages);
const getSellerPageMock = jest.mocked(getSellerPage);
const loginMock = jest.mocked(loginOrCreateAccount);
const updateListingMock = jest.mocked(updateListing);

const SELLER_PAGE: SellerPageResponse = {
  seller: { phoneNumber: '01000000000' },
  summary: { listingCount: 2, messageCount: 2 },
  listings: [
    {
      id: 101,
      title: '가상 테스트 책상',
      thumbnailUrl: 'https://example.com/fixtures/desk.webp',
      price: 45000,
      pickupTimeText: '평일 오후 7시 이후',
      canHelpMove: true,
      messageCount: 1,
      latestMessageAt: '2026-08-21T14:00:00+09:00',
      createdAt: '2026-08-20T14:00:00+09:00',
    },
    {
      id: 102,
      title: '가상 테스트 의자',
      thumbnailUrl: 'https://example.com/fixtures/chair.webp',
      pickupTimeText: '주말 오전',
      canHelpMove: false,
      messageCount: 1,
      latestMessageAt: '2026-08-21T15:00:00+09:00',
      createdAt: '2026-08-19T14:00:00+09:00',
    },
  ],
};

const EMPTY_SELLER_PAGE: SellerPageResponse = {
  seller: { phoneNumber: '01000000000' },
  summary: { listingCount: 0, messageCount: 0 },
  listings: [],
};

const LISTING_DETAIL: ListingDetailResponse = {
  id: 101,
  title: '가상 테스트 책상',
  description: '테스트 전용 설명입니다.',
  price: 45000,
  pickupTimeText: '평일 오후 7시 이후',
  canHelpMove: true,
  images: [
    {
      id: 1001,
      url: 'https://example.com/fixtures/desk.webp',
      displayOrder: 1,
    },
  ],
  createdAt: '2026-08-20T14:00:00+09:00',
  updatedAt: '2026-08-20T14:00:00+09:00',
};

const ROUTES: RouteObject[] = [
  { path: '/', element: <h1>마켓 홈</h1> },
  { path: '/inbox', element: <InboxPage /> },
  { path: '/mine', element: <MinePage /> },
  { path: '/mine/new', element: <ListingFormPage /> },
  { path: '/mine/:listingId/edit', element: <ListingFormPage /> },
];

function TestRoutes() {
  return useRoutes(ROUTES);
}

function renderRoute(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <TestRoutes />
    </MemoryRouter>,
  );
}

beforeAll(() => {
  Object.defineProperty(window, 'requestAnimationFrame', {
    configurable: true,
    value: (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    },
  });
  Object.defineProperty(window, 'cancelAnimationFrame', {
    configurable: true,
    value: jest.fn(),
  });
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: jest.fn(() => 'blob:marketplace-test-image'),
  });
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: jest.fn(),
  });
});

beforeEach(() => {
  jest.clearAllMocks();
  createListingMock.mockResolvedValue({
    listingId: 103,
    createdAt: '2026-08-21T16:00:00+09:00',
  });
  deleteListingMock.mockResolvedValue();
  getListingDetailMock.mockResolvedValue(LISTING_DETAIL);
  loginMock.mockResolvedValue({ phoneNumber: '01012345678' });
  updateListingMock.mockResolvedValue();
});

test('세션이 없으면 로그인 오버레이를 띄우고 인증 후 원래 내 매물을 다시 조회한다', async () => {
  const user = userEvent.setup();
  getSellerPageMock
    .mockRejectedValueOnce(
      new MarketplaceApiError(401, {
        code: 'AUTHENTICATION_REQUIRED',
        message: '로그인이 필요합니다.',
      }),
    )
    .mockResolvedValueOnce(EMPTY_SELLER_PAGE);

  renderRoute('/mine');

  expect(
    await screen.findByRole('heading', { name: '로그인이 필요해요' }),
  ).toBeInTheDocument();
  const phoneInput = screen.getByLabelText('휴대폰 번호');
  expect(phoneInput).toHaveFocus();
  expect(screen.queryByRole('heading', { name: '내 매물' })).not.toBeInTheDocument();

  screen.getByRole('button', { name: '확인' }).focus();
  await user.tab();
  expect(phoneInput).toHaveFocus();

  await user.type(phoneInput, '010-1234-5678');
  await user.type(screen.getByLabelText('비밀번호'), '1234');
  await user.click(screen.getByRole('button', { name: '확인' }));

  expect(loginMock).toHaveBeenCalledWith({
    phoneNumber: '01012345678',
    password: '1234',
  });
  expect(
    await screen.findByRole('heading', { name: '아직 올린 가구가 없어요' }),
  ).toBeInTheDocument();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(getSellerPageMock).toHaveBeenCalledTimes(2);
});

test('내 매물별 쪽지를 하나로 합쳐 최신 문의부터 익명으로 표시한다', async () => {
  getSellerPageMock.mockResolvedValue(SELLER_PAGE);
  getListingMessagesMock.mockImplementation(async (listingId) => ({
    listingId,
    items:
      listingId === 101
        ? [
            {
              id: 201,
              content: '오래된 가상 문의입니다.',
              createdAt: '2026-08-21T14:00:00+09:00',
            },
          ]
        : [
            {
              id: 202,
              content: '최신 가상 문의입니다.',
              createdAt: '2026-08-21T15:00:00+09:00',
            },
          ],
  }));

  renderRoute('/inbox');

  const cards = await screen.findAllByRole('listitem');
  expect(within(cards[0]!).getByText('최신 가상 문의입니다.')).toBeInTheDocument();
  expect(within(cards[0]!).getByText('가상 테스트 의자')).toBeInTheDocument();
  expect(within(cards[1]!).getByText('오래된 가상 문의입니다.')).toBeInTheDocument();
  expect(screen.getAllByText('익명 문의')).toHaveLength(2);
  expect(screen.queryByRole('button', { name: /답장/ })).not.toBeInTheDocument();
  expect(getListingMessagesMock).toHaveBeenCalledTimes(2);
});

test('등록 폼은 이미지 규칙을 검증하고 유효한 multipart 입력만 API에 넘긴다', async () => {
  const user = userEvent.setup();
  getSellerPageMock.mockResolvedValue(EMPTY_SELLER_PAGE);
  renderRoute('/mine/new');

  await screen.findByRole('heading', { name: '가구 올리기' });
  await user.type(await screen.findByLabelText('물품명'), '가상 테스트 협탁');
  await user.type(screen.getByLabelText(/상세 설명/), '테스트 전용 매물 설명입니다.');
  await user.type(screen.getByLabelText(/픽업 가능 시간/), '토요일 오전');
  await user.click(screen.getByRole('button', { name: '등록하기' }));

  expect(screen.getByText('사진을 1~5장 선택해 주세요.')).toBeInTheDocument();
  expect(createListingMock).not.toHaveBeenCalled();

  const imageInput = screen.getByLabelText('사진 추가');
  fireEvent.change(imageInput, {
    target: { files: [new File(['gif'], 'invalid.gif', { type: 'image/gif' })] },
  });
  expect(screen.getByText('JPEG, PNG, WebP 사진만 올릴 수 있어요.')).toBeInTheDocument();

  const validImage = new File(['webp'], 'virtual-table.webp', {
    type: 'image/webp',
  });
  fireEvent.change(imageInput, { target: { files: [validImage] } });
  await user.type(screen.getByLabelText('가격'), '30000');
  await user.click(screen.getByRole('button', { name: '등록하기' }));

  await waitFor(() => expect(createListingMock).toHaveBeenCalledTimes(1));
  expect(createListingMock).toHaveBeenCalledWith({
    title: '가상 테스트 협탁',
    description: '테스트 전용 매물 설명입니다.',
    price: 30000,
    pickupTimeText: '토요일 오전',
    canHelpMove: false,
    images: [validImage],
  });
  expect(
    await screen.findByRole('heading', { name: '아직 올린 가구가 없어요' }),
  ).toBeInTheDocument();
});

test('수정 화면은 기존 사진을 읽기 전용으로 유지하고 텍스트 필드만 갱신한다', async () => {
  const user = userEvent.setup();
  getSellerPageMock.mockResolvedValue(SELLER_PAGE);
  renderRoute('/mine/101/edit');

  expect(await screen.findByAltText('등록된 사진 1')).toHaveAttribute(
    'src',
    'https://example.com/fixtures/desk.webp',
  );
  expect(screen.queryByLabelText('사진 추가')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '수정하기' }));
  expect(screen.getByRole('alert')).toHaveTextContent('변경된 내용을 입력해 주세요.');
  expect(updateListingMock).not.toHaveBeenCalled();

  const titleInput = screen.getByLabelText('물품명');
  await user.clear(titleInput);
  await user.type(titleInput, '수정한 가상 테스트 책상');
  await user.click(screen.getByRole('button', { name: '수정하기' }));

  await waitFor(() => expect(updateListingMock).toHaveBeenCalledTimes(1));
  expect(updateListingMock).toHaveBeenCalledWith(101, {
    title: '수정한 가상 테스트 책상',
  });
});

test('내 매물은 가격을 표시하고, 가격이 없는 매물은 표시불가로 안내한다', async () => {
  getSellerPageMock.mockResolvedValue(SELLER_PAGE);
  renderRoute('/mine');

  const cards = await screen.findAllByRole('listitem');
  expect(within(cards[0]!).getByText('45,000원')).toBeInTheDocument();
  expect(within(cards[1]!).getByText('표시불가')).toBeInTheDocument();
});

test('삭제 확인 뒤 매물 삭제 API를 호출하고 목록을 다시 불러온다', async () => {
  const user = userEvent.setup();
  getSellerPageMock
    .mockResolvedValueOnce(SELLER_PAGE)
    .mockResolvedValueOnce(EMPTY_SELLER_PAGE);
  renderRoute('/mine');

  const deleteButtons = await screen.findAllByRole('button', { name: '삭제' });
  await user.click(deleteButtons[0]!);
  const dialog = screen.getByRole('alertdialog');
  expect(within(dialog).getByRole('button', { name: '취소' })).toHaveFocus();
  expect(within(dialog).getByText(/연결된 쪽지도 함께 삭제/)).toBeInTheDocument();
  await user.click(within(dialog).getByRole('button', { name: '삭제' }));

  await waitFor(() => expect(deleteListingMock).toHaveBeenCalledWith(101));
  expect(
    await screen.findByRole('heading', { name: '아직 올린 가구가 없어요' }),
  ).toBeInTheDocument();
  expect(getSellerPageMock).toHaveBeenCalledTimes(2);
});
