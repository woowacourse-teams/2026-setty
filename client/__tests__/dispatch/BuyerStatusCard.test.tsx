import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate, useRoutes } from 'react-router-dom';
import { dispatchRoutes } from '@/app/routes/dispatchRoutes';
import { API_ORIGIN } from '@/shared/api/http';

const BUYER_TOKEN = 'buyer-token-test';
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

const COMPLETED_REQUEST = {
  ...PENDING_REQUEST,
  status: 'FINAL_REVIEW_PENDING',
  sellerInputCompleted: true,
};

const jsonResponse = (status: number, body: unknown): Response =>
  ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  }) as Response;

const mockFetch = jest.fn<Promise<Response>, [string, RequestInit | undefined]>();
const clipboardWriteText = jest.fn<Promise<void>, [string]>();

function TestRoutes() {
  const element = useRoutes(dispatchRoutes);
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <>
      <output data-testid="route-path">{location.pathname}</output>
      <button
        type="button"
        onClick={() => navigate(`/dispatch/${BUYER_TOKEN}`)}
        data-testid="navigate-valid-buyer"
      >
        테스트용 구매자 카드 이동
      </button>
      {element}
    </>
  );
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <TestRoutes />
    </MemoryRouter>,
  );
}

async function submitBuyerRequest(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: '거래 링크 만들기' }));
  await user.type(screen.getByLabelText('상품명'), '3인용 소파');
  await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
  await user.type(screen.getByLabelText('연락처'), '01000000000');
  await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
  await user.click(screen.getByRole('checkbox', { name: '(필수) 개인정보 수집·이용 동의' }));
  await user.click(screen.getByRole('button', { name: '링크 생성하기' }));
}

beforeEach(() => {
  jest.useRealTimers();
  mockFetch.mockReset();
  clipboardWriteText.mockReset();
  clipboardWriteText.mockResolvedValue(undefined);
  global.fetch = mockFetch as unknown as typeof fetch;
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: clipboardWriteText },
  });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('Issue #14 구매자 상태 카드', () => {
  it('요청 생성 후 구매자 직접 URL로 이동하고 조회 응답의 링크를 표시한다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
      )
      .mockResolvedValueOnce(jsonResponse(200, PENDING_REQUEST));

    renderAt('/');

    await submitBuyerRequest(user);

    expect(
      await screen.findByRole('heading', { name: '거래가 시작됐어요' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '링크 복사' }));

    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('route-path')).toHaveTextContent(
      `/dispatch/${BUYER_TOKEN}`,
    );
    expect(screen.getByRole('textbox', { name: '판매자 입력 링크' })).toHaveValue(
      SELLER_INPUT_URL,
    );
    expect(mockFetch).toHaveBeenLastCalledWith(
      `${API_ORIGIN}/api/dispatch-requests/${BUYER_TOKEN}`,
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('링크 자동 복사가 실패해도 독립 버튼으로 구매자 카드에 진입한다', async () => {
    const user = userEvent.setup();
    clipboardWriteText.mockRejectedValue(new Error('FAKE_CLIPBOARD_ERROR'));
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: clipboardWriteText },
    });
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
      )
      .mockResolvedValueOnce(jsonResponse(200, PENDING_REQUEST));
    renderAt('/');

    await submitBuyerRequest(user);
    expect(
      await screen.findByRole('heading', { name: '거래가 시작됐어요' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '링크 복사' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '링크를 복사하지 못했어요.',
    );

    await user.click(screen.getByRole('button', { name: '판매자 입력 상태 확인하기' }));

    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('route-path')).toHaveTextContent(
      `/dispatch/${BUYER_TOKEN}`,
    );
  });

  it('직접 URL을 새로 렌더링해도 React 메모리 없이 같은 카드를 복구한다', async () => {
    mockFetch.mockResolvedValue(jsonResponse(200, PENDING_REQUEST));

    const firstVisit = renderAt(`/dispatch/${BUYER_TOKEN}`);
    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '판매자 입력 링크' })).toHaveValue(
      SELLER_INPUT_URL,
    );

    firstVisit.unmount();
    renderAt(`/dispatch/${BUYER_TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '판매자 입력 링크' })).toHaveValue(
      SELLER_INPUT_URL,
    );
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });

  it('카드에서 판매자 입력 링크를 복사하고 성공을 안내한다', async () => {
    mockFetch.mockResolvedValue(jsonResponse(200, PENDING_REQUEST));
    renderAt(`/dispatch/${BUYER_TOKEN}`);

    await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' });
    await userEvent.click(screen.getByRole('button', { name: '링크 복사' }));

    expect(clipboardWriteText).toHaveBeenCalledWith(SELLER_INPUT_URL);
    expect(await screen.findByText('판매자 입력 링크를 복사했어요.')).toHaveAttribute(
      'role',
      'status',
    );
  });

  it('Clipboard API가 실패해도 링크를 직접 선택할 수 있게 유지한다', async () => {
    clipboardWriteText.mockRejectedValueOnce(new Error('FAKE_CLIPBOARD_ERROR'));
    mockFetch.mockResolvedValue(jsonResponse(200, PENDING_REQUEST));
    renderAt(`/dispatch/${BUYER_TOKEN}`);

    await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' });
    await userEvent.click(screen.getByRole('button', { name: '링크 복사' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '링크를 직접 선택해 복사해 주세요.',
    );
    expect(screen.getByRole('textbox', { name: '판매자 입력 링크' })).toHaveValue(
      SELLER_INPUT_URL,
    );
  });

  it('Clipboard API를 지원하지 않는 브라우저에서도 수동 복사 링크를 제공한다', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: undefined,
    });
    mockFetch.mockResolvedValue(jsonResponse(200, PENDING_REQUEST));
    renderAt(`/dispatch/${BUYER_TOKEN}`);

    await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' });
    await userEvent.click(screen.getByRole('button', { name: '링크 복사' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '링크를 직접 선택해 복사해 주세요.',
    );
    expect(screen.getByRole('textbox', { name: '판매자 입력 링크' })).toHaveValue(
      SELLER_INPUT_URL,
    );
  });

  it('5초 폴링으로 완료 상태를 갱신하고 완료 후에는 폴링을 멈춘다', async () => {
    jest.useFakeTimers();
    mockFetch
      .mockResolvedValueOnce(jsonResponse(200, PENDING_REQUEST))
      .mockResolvedValueOnce(jsonResponse(200, COMPLETED_REQUEST));

    const view = renderAt(`/dispatch/${BUYER_TOKEN}`);
    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();

    await act(async () => {
      await jest.advanceTimersByTimeAsync(5000);
    });

    expect(
      await screen.findByRole('heading', { name: '판매자 입력이 완료됐어요' }),
    ).toBeInTheDocument();
    expect(screen.getByText('배송 조건을 확인하고 있어요')).toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', { name: '판매자 입력 링크' }),
    ).not.toBeInTheDocument();
    expect(mockFetch).toHaveBeenCalledTimes(2);

    await act(async () => {
      await jest.advanceTimersByTimeAsync(5000);
    });
    expect(mockFetch).toHaveBeenCalledTimes(2);

    view.unmount();
  });

  it('알 수 없는 buyerToken의 404를 오류로 표시하고 카드를 만들지 않는다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(404, { message: '배차 요청을 찾을 수 없습니다.' }),
    );
    renderAt('/dispatch/unknown-buyer-token');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '배차 요청을 찾을 수 없습니다.',
    );
    expect(screen.queryByLabelText('판매자 입력 링크')).not.toBeInTheDocument();
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('같은 Router에서 buyerToken이 바뀌면 이전 오류를 버리고 새 카드를 조회한다', async () => {
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(404, { message: '배차 요청을 찾을 수 없습니다.' }),
      )
      .mockResolvedValueOnce(jsonResponse(200, PENDING_REQUEST));
    renderAt('/dispatch/unknown-buyer-token');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '배차 요청을 찾을 수 없습니다.',
    );

    await userEvent.click(screen.getByTestId('navigate-valid-buyer'));

    expect(
      await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '판매자 입력 링크' })).toHaveValue(
      SELLER_INPUT_URL,
    );
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });

  it('응답에 포함되지 않아야 할 판매자 개인정보를 화면에 렌더링하지 않는다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(200, {
        ...PENDING_REQUEST,
        sellerName: '노출 금지 가상판매자',
        sellerPhoneNumber: '010-1111-1111',
        pickupAddress: '노출 금지 가상주소',
      }),
    );
    renderAt(`/dispatch/${BUYER_TOKEN}`);

    await screen.findByRole('heading', { name: '판매자를 기다리고 있어요' });
    expect(screen.queryByText('노출 금지 가상판매자')).not.toBeInTheDocument();
    expect(screen.queryByText('010-1111-1111')).not.toBeInTheDocument();
    expect(screen.queryByText('노출 금지 가상주소')).not.toBeInTheDocument();
  });
});
