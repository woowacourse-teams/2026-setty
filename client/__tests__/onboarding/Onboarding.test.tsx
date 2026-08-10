import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import App from '@/app/App';
import { completeOnboarding } from '@/app/onboarding/onboardingStorage';

/**
 * 온보딩은 기기당 한 번만 보여주고, 끝나면 원래 홈 화면 흐름으로 이어져야 한다.
 * 노출 여부가 localStorage에 남으므로 테스트마다 저장소를 비운다.
 */

const fetchMock = jest.fn() as jest.MockedFunction<typeof fetch>;

beforeEach(() => {
  window.localStorage.clear();
  fetchMock.mockReset();
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    value: fetchMock,
    writable: true,
  });
});

function RoutePath() {
  const { pathname } = useLocation();

  return <output data-testid="route-path">{pathname}</output>;
}

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <RoutePath />
      <App />
    </MemoryRouter>,
  );

const currentPath = () => screen.getByTestId('route-path').textContent;

test('처음 방문한 기기는 홈에서 온보딩 첫 화면을 본다', () => {
  renderAt('/');

  expect(currentPath()).toBe('/onboarding/1');
  expect(screen.getByRole('heading', { name: /SETTY가 도와드려요/ })).toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: '거래 링크 만들기' }),
  ).not.toBeInTheDocument();
});

test('다음으로 마지막 화면까지 이동하면 예상 견적으로 갈 수 있다', async () => {
  const user = userEvent.setup();
  renderAt('/');

  await user.click(screen.getByRole('button', { name: '다음' }));
  expect(screen.getByRole('heading', { name: /서로 몰라도 돼요/ })).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '다음' }));
  expect(screen.getByRole('heading', { name: /SETTY가 불러드려요/ })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '건너뛰기' })).not.toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '예상 견적 확인하기' }));

  expect(currentPath()).toBe('/estimate');
  expect(screen.getByRole('heading', { name: '예상 견적 확인' })).toBeInTheDocument();
});

test('마지막 화면의 보조 action은 배차 요청 폼으로 이동한다', async () => {
  const user = userEvent.setup();
  renderAt('/onboarding/3');

  await user.click(screen.getByRole('button', { name: /거래 링크 만들어보기/ }));

  expect(currentPath()).toBe('/dispatch/new');
});

test('건너뛰기를 누르면 바로 홈 화면으로 간다', async () => {
  const user = userEvent.setup();
  renderAt('/');

  await user.click(screen.getByRole('button', { name: '건너뛰기' }));

  expect(currentPath()).toBe('/');
  expect(screen.getByRole('button', { name: '거래 링크 만들기' })).toBeInTheDocument();
});

test('온보딩을 끝낸 기기는 홈에서 온보딩을 다시 보지 않는다', async () => {
  const user = userEvent.setup();
  const first = renderAt('/');

  await user.click(screen.getByRole('button', { name: '건너뛰기' }));
  first.unmount();

  renderAt('/');

  expect(currentPath()).toBe('/');
  expect(screen.getByRole('button', { name: '거래 링크 만들기' })).toBeInTheDocument();
});

test('범위 밖 단계 URL은 첫 화면으로 되돌린다', () => {
  renderAt('/onboarding/9');

  expect(currentPath()).toBe('/onboarding/1');
  expect(screen.getByRole('heading', { name: /SETTY가 도와드려요/ })).toBeInTheDocument();
});

test('온보딩 완료 여부와 무관하게 판매자 입력 링크는 온보딩을 거치지 않는다', () => {
  renderAt('/seller-input/seller-token-test');

  expect(currentPath()).toBe('/seller-input/seller-token-test');
});

test('completeOnboarding을 부른 기기는 홈이 바로 열린다', () => {
  completeOnboarding();

  renderAt('/');

  expect(currentPath()).toBe('/');
});
