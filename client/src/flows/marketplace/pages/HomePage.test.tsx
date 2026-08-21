import { beforeEach, expect, jest, test } from '@jest/globals';
import '@testing-library/jest-dom/jest-globals';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  MemoryRouter,
  useLocation,
  useNavigate,
  type RouteObject,
  useRoutes,
} from 'react-router-dom';
import { getListings, type ListingSummary } from '@/flows/marketplace/api/marketplaceApi';
import HomePage from './HomePage';

jest.mock('@/flows/marketplace/api/marketplaceApi', () => ({
  getListings: jest.fn(),
}));

const getListingsMock = jest.mocked(getListings);

Object.defineProperty(window, 'PointerEvent', {
  configurable: true,
  value: MouseEvent,
});

const LISTINGS: ListingSummary[] = [
  {
    id: 11,
    title: '테스트 원목 책상',
    thumbnailUrl: 'https://example.com/fake-desk.jpg',
    pickupTimeText: '평일 오후 7시 이후',
    canHelpMove: true,
    createdAt: '2026-08-21T14:00:00+09:00',
  },
  {
    id: 12,
    title: '테스트 소형 냉장고',
    thumbnailUrl: 'https://example.com/fake-fridge.jpg',
    pickupTimeText: '주말 오전',
    canHelpMove: false,
    createdAt: '2026-08-21T13:00:00+09:00',
  },
];

function Destination({ kind }: { kind: 'detail' | 'message' }) {
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as { marketplaceDeckConsumed?: boolean } | null;

  return (
    <main>
      <h1>{kind === 'detail' ? '상세 도착' : '쪽지 도착'}</h1>
      <p>{state?.marketplaceDeckConsumed ? '카드 소비됨' : '카드 유지됨'}</p>
      <button onClick={() => navigate(-1)} type="button">
        돌아가기
      </button>
    </main>
  );
}

const ROUTES: RouteObject[] = [
  { path: '/', element: <HomePage /> },
  { path: '/listings/:id', element: <Destination kind="detail" /> },
  { path: '/listings/:id/message', element: <Destination kind="message" /> },
];

function TestRoutes() {
  return useRoutes(ROUTES);
}

function renderHome() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <TestRoutes />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  getListingsMock.mockReset();
  getListingsMock.mockResolvedValue(LISTINGS);
  window.sessionStorage.clear();
});

test('API 계약에 있는 목록 정보만 카드에 표시한다', async () => {
  renderHome();

  expect(
    await screen.findByRole('heading', { name: '테스트 원목 책상' }),
  ).toBeInTheDocument();
  expect(screen.getByText('평일 오후 7시 이후')).toBeInTheDocument();
  expect(screen.getByText('판매자가 운반을 도와드릴 수 있어요')).toBeInTheDocument();
  expect(screen.queryByText('8만원')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /찜/ })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '내 매물' })).toHaveAttribute('href', '/mine');
  expect(screen.getByRole('link', { name: '쪽지함' })).toHaveAttribute('href', '/inbox');
});

test('카드를 눌러 상세를 열면 돌아왔을 때 같은 카드를 유지한다', async () => {
  const user = userEvent.setup();
  renderHome();

  await user.click(
    await screen.findByRole('button', { name: '테스트 원목 책상 상세 보기' }),
  );
  expect(screen.getByRole('heading', { name: '상세 도착' })).toBeInTheDocument();
  expect(screen.getByText('카드 유지됨')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '돌아가기' }));
  expect(
    await screen.findByRole('heading', { name: '테스트 원목 책상' }),
  ).toBeInTheDocument();
});

test('상세 보기 동작은 카드를 소비하고 돌아오면 다음 카드를 보여준다', async () => {
  const user = userEvent.setup();
  renderHome();

  await screen.findByRole('heading', { name: '테스트 원목 책상' });
  await user.click(screen.getByRole('button', { name: '이 매물 상세 보기' }));
  expect(screen.getByText('카드 소비됨')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '돌아가기' }));
  expect(
    await screen.findByRole('heading', { name: '테스트 소형 냉장고' }),
  ).toBeInTheDocument();
});

test('다음 매물과 되돌리기 버튼으로 덱을 이동한다', async () => {
  const user = userEvent.setup();
  renderHome();

  await screen.findByRole('heading', { name: '테스트 원목 책상' });
  await user.click(screen.getByRole('button', { name: '다음 매물 보기' }));
  expect(screen.getByRole('heading', { name: '테스트 소형 냉장고' })).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '이전 카드로 되돌리기' }));
  expect(screen.getByRole('heading', { name: '테스트 원목 책상' })).toBeInTheDocument();
});

test('90px 이상 왼쪽으로 끌면 다음 카드로 이동한다', async () => {
  renderHome();
  const card = await screen.findByRole('button', { name: '테스트 원목 책상 상세 보기' });

  fireEvent.pointerDown(card, { button: 0, clientX: 200, pointerId: 1, isPrimary: true });
  fireEvent.pointerMove(card, { clientX: 109, pointerId: 1, isPrimary: true });
  fireEvent.pointerUp(card, { button: 0, clientX: 109, pointerId: 1, isPrimary: true });

  expect(screen.getByRole('heading', { name: '테스트 소형 냉장고' })).toBeInTheDocument();
});

test('덱을 모두 본 뒤 다시 보기는 목록을 새로 조회하고 첫 카드로 돌아간다', async () => {
  const user = userEvent.setup();
  renderHome();

  await screen.findByRole('heading', { name: '테스트 원목 책상' });
  await user.click(screen.getByRole('button', { name: '다음 매물 보기' }));
  await user.click(screen.getByRole('button', { name: '다음 매물 보기' }));
  expect(
    screen.getByRole('heading', { name: '오늘 동네 가구는 끝' }),
  ).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '다시 보기' }));
  await waitFor(() => expect(getListingsMock).toHaveBeenCalledTimes(2));
  expect(
    await screen.findByRole('heading', { name: '테스트 원목 책상' }),
  ).toBeInTheDocument();
});
