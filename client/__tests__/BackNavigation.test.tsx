import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import App from '@/app/App';

const fetchMock = jest.fn() as jest.MockedFunction<typeof fetch>;

const summary = {
  id: 11,
  title: '테스트 원목 책상',
  thumbnailUrl: 'https://example.test/fake-desk.jpg',
  pickupTimeText: '평일 오후 7시 이후',
  canHelpMove: true,
  createdAt: '2026-08-21T14:00:00+09:00',
};

const detail = {
  ...summary,
  description: '상태가 좋은 책상입니다.',
  images: [{ id: 1, url: summary.thumbnailUrl, displayOrder: 0 }],
  updatedAt: '2026-08-21T14:00:00+09:00',
};

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => body,
  } as Response;
}

function RouteHarness() {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  return (
    <>
      <output data-testid="route-path">{pathname}</output>
      <button type="button" onClick={() => navigate(-1)}>
        브라우저 뒤로가기
      </button>
    </>
  );
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <RouteHarness />
      <App />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  window.sessionStorage.clear();
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    value: fetchMock,
    writable: true,
  });
});

test('카드 상세를 닫으면 홈의 같은 카드로 돌아간다', async () => {
  const user = userEvent.setup();
  fetchMock.mockResolvedValue(jsonResponse({ items: [summary] }));
  fetchMock
    .mockResolvedValueOnce(jsonResponse({ items: [summary] }))
    .mockResolvedValueOnce(jsonResponse(detail));
  renderAt('/');

  await user.click(
    await screen.findByRole('button', { name: '테스트 원목 책상 상세 보기' }),
  );
  expect(screen.getByTestId('route-path')).toHaveTextContent('/listings/11');
  expect(
    await screen.findByRole('heading', { name: '테스트 원목 책상' }),
  ).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '닫기' }));

  expect(screen.getByTestId('route-path')).toHaveTextContent('/');
  expect(
    await screen.findByRole('heading', { name: '테스트 원목 책상' }),
  ).toBeInTheDocument();
});

test('상세에서 쪽지를 열고 브라우저 뒤로가면 상세로 복귀한다', async () => {
  const user = userEvent.setup();
  fetchMock.mockResolvedValue(jsonResponse(detail));
  renderAt('/listings/11');

  await user.click(await screen.findByRole('button', { name: '쪽지 보내기' }));
  expect(screen.getByTestId('route-path')).toHaveTextContent('/listings/11/message');
  expect(screen.getByRole('heading', { name: '쪽지 보내기' })).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '브라우저 뒤로가기' }));

  expect(screen.getByTestId('route-path')).toHaveTextContent('/listings/11');
  expect(
    await screen.findByRole('heading', { name: '테스트 원목 책상' }),
  ).toBeInTheDocument();
});

test('직접 연 상세의 닫기 버튼은 홈으로 안전하게 보낸다', async () => {
  const user = userEvent.setup();
  fetchMock
    .mockResolvedValueOnce(jsonResponse(detail))
    .mockResolvedValueOnce(jsonResponse({ items: [summary] }));
  renderAt('/listings/11');

  await user.click(await screen.findByRole('button', { name: '닫기' }));

  expect(screen.getByTestId('route-path')).toHaveTextContent('/');
  expect(
    await screen.findByRole('heading', { name: '테스트 원목 책상' }),
  ).toBeInTheDocument();
});
