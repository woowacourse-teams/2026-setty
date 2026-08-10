import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import App from '@/app/App';

/**
 * 브라우저 뒤로가기 회귀 테스트.
 *
 * 화면 단계를 컴포넌트 state로만 들고 있으면 히스토리 항목이 쌓이지 않아
 * 뒤로가기가 이전 단계 대신 사이트 밖으로 나간다. 각 단계가 URL을 갖는지,
 * 되돌아가면 안 되는 제출 화면만 히스토리를 덮어쓰는지 확인한다.
 */

const BUYER_TOKEN = 'buyer-token-test';
const SELLER_TOKEN = 'seller-token-test';
const SELLER_INPUT_URL = 'https://example.test/seller-input/seller-token-test';

const PENDING_REQUEST = {
  status: 'SELLER_INPUT_PENDING',
  buyerName: '가상구매자',
  buyerPhoneNumber: '010-0000-0000',
  deliveryAddress: '가상시 가상구 가상로 1',
  itemType: '3인용 소파',
  highValueItem: false,
  sellerInputCompleted: false,
  createdAt: '2026-08-07T10:00:00+09:00',
  sellerInputUrl: SELLER_INPUT_URL,
};

const jsonResponse = (status: number, body: unknown): Response =>
  ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  }) as Response;

const emptyResponse = (status: number): Response =>
  ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new Error('no body');
    },
  }) as Response;

const mockFetch = jest.fn<Promise<Response>, [string, RequestInit | undefined]>();
const clipboardWriteText = jest.fn<Promise<void>, [string]>();

/** 브라우저 뒤로가기 버튼을 대신한다. */
function BackNavigationHarness() {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  return (
    <>
      <output data-testid="route-path">{pathname}</output>
      <button data-testid="browser-back" type="button" onClick={() => navigate(-1)}>
        브라우저 뒤로가기
      </button>
    </>
  );
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <BackNavigationHarness />
      <App />
    </MemoryRouter>,
  );
}

const currentPath = () => screen.getByTestId('route-path').textContent;

const goBack = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByTestId('browser-back'));

const agreePrivacyConsent = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('checkbox', { name: '(필수) 개인정보 수집·이용 동의' }));

async function submitBuyerRequest(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('상품명'), '3인용 소파');
  await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
  await user.type(screen.getByLabelText('연락처'), '01000000000');
  await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
  await agreePrivacyConsent(user);
  await user.click(screen.getByRole('button', { name: '링크 생성하기' }));
}

beforeEach(() => {
  mockFetch.mockReset();
  clipboardWriteText.mockReset();
  clipboardWriteText.mockResolvedValue(undefined);
  global.fetch = mockFetch as unknown as typeof fetch;
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: clipboardWriteText },
  });
});

describe('배차 flow 뒤로가기', () => {
  it('거래 링크 폼에서 뒤로가면 홈으로 돌아간다', async () => {
    const user = userEvent.setup();
    renderAt('/');

    await user.click(screen.getByRole('button', { name: '거래 링크 만들기' }));
    expect(currentPath()).toBe('/dispatch/new');

    await goBack(user);

    expect(currentPath()).toBe('/');
    expect(screen.getByRole('heading', { name: /거래를 시작하세요/ })).toBeInTheDocument();
  });

  it('화면 안 뒤로가기 버튼도 히스토리를 되감아 홈으로 돌아간다', async () => {
    const user = userEvent.setup();
    renderAt('/');

    await user.click(screen.getByRole('button', { name: '거래 링크 만들기' }));
    await user.click(screen.getByRole('button', { name: '뒤로 가기' }));

    expect(currentPath()).toBe('/');
    expect(screen.getByRole('heading', { name: /거래를 시작하세요/ })).toBeInTheDocument();
  });

  it('폼 URL로 바로 들어온 경우 뒤로가기 버튼이 사이트를 벗어나지 않고 홈으로 보낸다', async () => {
    const user = userEvent.setup();
    renderAt('/dispatch/new');

    await user.click(screen.getByRole('button', { name: '뒤로 가기' }));

    expect(currentPath()).toBe('/');
    expect(screen.getByRole('heading', { name: /거래를 시작하세요/ })).toBeInTheDocument();
  });

  it('구매자 상태 카드에서 뒤로가면 링크 화면을 거쳐 홈까지 돌아간다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
      )
      .mockResolvedValue(jsonResponse(200, PENDING_REQUEST));

    renderAt('/');
    await user.click(screen.getByRole('button', { name: '거래 링크 만들기' }));
    await submitBuyerRequest(user);

    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '링크 복사' }));
    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();
    expect(currentPath()).toBe(`/dispatch/${BUYER_TOKEN}`);

    await goBack(user);
    expect(currentPath()).toBe(`/dispatch/${BUYER_TOKEN}/link`);
    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();

    await goBack(user);
    expect(currentPath()).toBe('/');
    expect(screen.getByRole('heading', { name: /거래를 시작하세요/ })).toBeInTheDocument();
  });

  it('제출한 구매자 폼은 히스토리에 남기지 않아 같은 요청을 다시 만들 수 없다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
      )
      .mockResolvedValue(jsonResponse(200, PENDING_REQUEST));

    renderAt('/');
    await user.click(screen.getByRole('button', { name: '거래 링크 만들기' }));
    await submitBuyerRequest(user);
    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();

    await goBack(user);

    expect(currentPath()).toBe('/');
    expect(screen.queryByLabelText('상품명')).not.toBeInTheDocument();
  });

  it('제출한 판매자 폼도 히스토리에 남기지 않는다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: false }),
      )
      .mockResolvedValueOnce(emptyResponse(204));

    renderAt(`/seller-input/${SELLER_TOKEN}`);

    await user.type(await screen.findByLabelText('판매자 이름'), '가상판매자');
    await user.type(screen.getByLabelText('연락처'), '01000000001');
    await user.type(screen.getByLabelText('발송 주소'), '가상시 가상구 가상로 2');
    await user.type(screen.getByLabelText('회수 희망 시간'), '평일 오후 2시 이후');
    await user.click(screen.getByRole('button', { name: '제출하기' }));

    expect(await screen.findByText('정보가 제출됐어요')).toBeInTheDocument();
    expect(currentPath()).toBe(`/seller-input/${SELLER_TOKEN}/submitted`);
    // 제출 화면이 판매자 폼을 덮어썼으므로 되돌아갈 앱 내부 항목이 없다.
    expect(screen.queryByLabelText('판매자 이름')).not.toBeInTheDocument();
  });

  it('뒤로가기로 경로가 바뀌면 멈춰 있지 않고 해당 화면을 다시 그린다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(jsonResponse(200, PENDING_REQUEST));

    renderAt(`/dispatch/${BUYER_TOKEN}`);
    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '홈으로 돌아가기' }));
    expect(currentPath()).toBe('/');
    expect(screen.getByRole('heading', { name: /거래를 시작하세요/ })).toBeInTheDocument();

    await goBack(user);

    expect(currentPath()).toBe(`/dispatch/${BUYER_TOKEN}`);
    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();
  });
});

describe('예상 견적 flow 뒤로가기', () => {
  it('견적 폼에서 뒤로가면 배차 홈으로 돌아간다', async () => {
    const user = userEvent.setup();
    renderAt('/');

    await user.click(screen.getByRole('button', { name: '예상 견적 확인하기' }));
    expect(currentPath()).toBe('/estimate');

    await goBack(user);

    expect(currentPath()).toBe('/');
    expect(screen.getByRole('heading', { name: /거래를 시작하세요/ })).toBeInTheDocument();
  });

  it('접수 완료 화면은 견적 폼을 덮어써 재접수를 막는다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(jsonResponse(201, { id: 1 }));

    renderAt('/');
    await user.click(screen.getByRole('button', { name: '예상 견적 확인하기' }));

    await user.type(screen.getByLabelText('상품명'), '원목 의자');
    await user.type(screen.getByLabelText('거래 지역'), '가상구 가상동');
    await user.type(screen.getByLabelText('이름'), '가상사용자');
    await user.type(screen.getByLabelText('연락처'), '010-0000-0000');
    await agreePrivacyConsent(user);
    await user.click(screen.getByRole('button', { name: '예상 견적 요청하기' }));

    expect(
      await screen.findByRole('heading', { name: '견적 요청이 접수됐어요' }),
    ).toBeInTheDocument();
    expect(currentPath()).toBe('/estimate/submitted');

    await goBack(user);

    expect(currentPath()).toBe('/');
    expect(screen.queryByLabelText('거래 지역')).not.toBeInTheDocument();
  });
});
