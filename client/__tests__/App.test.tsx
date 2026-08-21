import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '@/app/App';

const fetchMock = jest.fn() as jest.MockedFunction<typeof fetch>;

const listing = {
  id: 11,
  title: '테스트 원목 책상',
  thumbnailUrl: 'https://example.test/fake-desk.jpg',
  pickupTimeText: '평일 오후 7시 이후',
  canHelpMove: true,
  createdAt: '2026-08-21T14:00:00+09:00',
};

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
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

test('루트 경로에서 미니마켓 홈을 연다', async () => {
  fetchMock.mockResolvedValue(jsonResponse({ items: [listing] }));

  render(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>,
  );

  expect(
    await screen.findByRole('heading', { name: '테스트 원목 책상' }),
  ).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '내 매물' })).toHaveAttribute('href', '/mine');
  expect(screen.getByRole('link', { name: '쪽지함' })).toHaveAttribute('href', '/inbox');
  expect(fetchMock).toHaveBeenCalledWith(
    expect.stringMatching(/\/api\/listings$/),
    expect.objectContaining({ credentials: 'include', method: 'GET' }),
  );
});

test('알 수 없는 경로는 공용 404와 홈 링크를 표시한다', () => {
  render(
    <MemoryRouter initialEntries={['/unknown']}>
      <App />
    </MemoryRouter>,
  );

  expect(
    screen.getByRole('heading', { name: '페이지를 찾을 수 없어요' }),
  ).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '가구 둘러보기' })).toHaveAttribute(
    'href',
    '/',
  );
  expect(fetchMock).not.toHaveBeenCalled();
});
