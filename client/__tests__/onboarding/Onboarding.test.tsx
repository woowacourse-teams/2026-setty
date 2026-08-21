import { render, screen } from '@testing-library/react';
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
});

test.each(['/onboarding/1', '/dispatch/new', '/estimate'])(
  '기존 SETTY 경로 %s는 앱 라우터에서 연결하지 않는다',
  (path) => {
    render(
      <MemoryRouter initialEntries={[path]}>
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
  },
);
