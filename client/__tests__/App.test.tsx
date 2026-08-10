import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import App from '@/app/App';

const fetchMock = jest.fn() as jest.MockedFunction<typeof fetch>;

beforeEach(() => {
  fetchMock.mockReset();
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    value: fetchMock,
    writable: true,
  });
  window.history.replaceState({}, '', '/');
});

test('루트 경로에서 배차를 시작하거나 예상 견적으로 이동할 수 있다', async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>,
  );

  expect(screen.getByRole('heading', { name: /번거로운 중고 가구 거래/ })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '거래 링크 만들기' })).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();

  await user.click(screen.getByRole('button', { name: '예상 견적 확인하기' }));

  expect(screen.getByRole('heading', { name: '예상 견적 확인' })).toBeInTheDocument();
});

test('알 수 없는 경로는 특정 flow를 권하는 대신 공용 오류를 표시한다', () => {
  render(
    <MemoryRouter initialEntries={['/unknown']}>
      <App />
    </MemoryRouter>,
  );

  expect(
    screen.getByRole('heading', { name: '페이지를 찾을 수 없어요' }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole('link', { name: '예상 견적 요청으로 이동' }),
  ).not.toBeInTheDocument();
});
