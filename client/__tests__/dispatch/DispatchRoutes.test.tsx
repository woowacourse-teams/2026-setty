import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useRoutes } from 'react-router-dom';
import { dispatchRoutes } from '@/app/routes/dispatchRoutes';
import { API_ORIGIN } from '@/shared/api/http';

/**
 * 배차 flow의 화면 전환과 server 계약 연결을 확인한다.
 * 화면 전환은 URL로 표현하므로 진입도 경로로 한다.
 * 개인정보를 남기지 않도록 명백한 가상 데이터만 쓴다.
 */

const BUYER_TOKEN = 'buyer-token-test';
const SELLER_TOKEN = 'seller-token-test';
const SELLER_INPUT_URL = 'http://localhost:5173/seller-input/seller-token-test';

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

function RoutesUnderTest() {
  const element = useRoutes(dispatchRoutes);
  const { pathname } = useLocation();

  return (
    <>
      <output data-testid="route-path">{pathname}</output>
      {element}
    </>
  );
}

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <RoutesUnderTest />
    </MemoryRouter>,
  );

const currentPath = () => screen.getByTestId('route-path').textContent;

beforeEach(() => {
  mockFetch.mockReset();
  global.fetch = mockFetch as unknown as typeof fetch;
});

const lastRequest = () => {
  const call = mockFetch.mock.calls.at(-1);
  if (!call) {
    throw new Error('fetch가 호출되지 않았습니다.');
  }

  return { url: call[0], init: call[1] };
};

describe('구매자 흐름', () => {
  it('홈에서 폼으로 이동해 server 계약대로 배차 요청을 만든다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
    );

    renderAt('/');

    expect(screen.getByRole('heading', { name: /거래를 시작하세요/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '거래 링크 만들기' }));
    expect(currentPath()).toBe('/dispatch/new');

    await user.type(screen.getByLabelText('상품명'), '3인용 소파');
    await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
    await user.type(screen.getByLabelText('연락처'), '01000000000');
    await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
    await user.click(screen.getByRole('button', { name: /50만원 이상/ }));
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    await waitFor(() => expect(mockFetch).toHaveBeenCalled());

    const { url, init } = lastRequest();
    expect(url).toBe(`${API_ORIGIN}/api/dispatch-requests`);
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual({
      itemType: '3인용 소파',
      buyerName: '가상구매자',
      buyerPhoneNumber: '01000000000',
      deliveryAddress: '가상시 가상구 가상로 1',
      highValueItem: true,
    });

    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();
    expect(currentPath()).toBe(`/dispatch/${BUYER_TOKEN}/link`);
  });

  it('server 오류 메시지를 성공으로 바꾸지 않고 그대로 보여준다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(400, { message: '입력값이 올바르지 않습니다: buyerPhoneNumber' }),
    );

    renderAt('/dispatch/new');

    await user.type(screen.getByLabelText('상품명'), '3인용 소파');
    await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
    await user.type(screen.getByLabelText('연락처'), '01000000000');
    await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(
      await screen.findByText('입력값이 올바르지 않습니다: buyerPhoneNumber'),
    ).toBeInTheDocument();
    expect(screen.queryByText('거래가 시작됐어요')).not.toBeInTheDocument();
    expect(currentPath()).toBe('/dispatch/new');
  });

  it('연락처 형식이 server @Pattern과 다르면 API를 호출하지 않는다', async () => {
    const user = userEvent.setup();

    renderAt('/dispatch/new');

    await user.type(screen.getByLabelText('상품명'), '3인용 소파');
    await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
    await user.type(screen.getByLabelText('연락처'), '123');
    await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    await waitFor(() => expect(screen.getByLabelText('연락처')).toBeInvalid());
    expect(mockFetch).not.toHaveBeenCalled();
  });
});

describe('링크 생성 화면 복구', () => {
  it('navigation state 없이 직접 들어오면 buyerToken으로 링크를 다시 조회한다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(200, {
        status: 'SELLER_INPUT_PENDING',
        buyerName: '가상구매자',
        buyerPhoneNumber: '010-0000-0000',
        deliveryAddress: '가상시 가상구 가상로 1',
        itemType: '3인용 소파',
        highValueItem: false,
        sellerInputCompleted: false,
        createdAt: '2026-08-07T10:00:00+09:00',
        sellerInputUrl: SELLER_INPUT_URL,
      }),
    );

    renderAt(`/dispatch/${BUYER_TOKEN}/link`);

    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();
    expect(mockFetch).toHaveBeenCalledWith(
      `${API_ORIGIN}/api/dispatch-requests/${BUYER_TOKEN}`,
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('조회 실패를 오류로 보여주고 링크를 지어내지 않는다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(404, { message: '배차 요청을 찾을 수 없습니다.' }),
    );

    renderAt(`/dispatch/${BUYER_TOKEN}/link`);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '배차 요청을 찾을 수 없습니다.',
    );
    expect(screen.queryByText('거래가 시작됐어요')).not.toBeInTheDocument();
  });
});

describe('판매자 흐름', () => {
  it('판매자 링크로 들어오면 세션을 조회하고 입력을 제출한다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: false }),
      )
      .mockResolvedValueOnce(emptyResponse(204));

    renderAt(`/seller-input/${SELLER_TOKEN}`);

    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith(
        `${API_ORIGIN}/api/dispatch-requests/seller-sessions/${SELLER_TOKEN}`,
        expect.objectContaining({ method: 'GET' }),
      ),
    );
    expect(await screen.findByText('3인용 소파')).toBeInTheDocument();

    await user.type(screen.getByLabelText('판매자 이름'), '가상판매자');
    await user.type(screen.getByLabelText('연락처'), '01000000001');
    await user.type(screen.getByLabelText('발송 주소'), '가상시 가상구 가상로 2');
    await user.type(screen.getByLabelText('회수 희망 시간'), '평일 오후 2시 이후');
    await user.click(screen.getByRole('button', { name: '제출하기' }));

    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(2));
    const { url, init } = lastRequest();
    expect(url).toBe(
      `${API_ORIGIN}/api/dispatch-requests/seller-sessions/${SELLER_TOKEN}`,
    );
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual({
      sellerName: '가상판매자',
      sellerPhoneNumber: '01000000001',
      pickupAddress: '가상시 가상구 가상로 2',
      availablePickupTime: '평일 오후 2시 이후',
    });

    expect(await screen.findByText('정보가 제출됐어요')).toBeInTheDocument();
    expect(currentPath()).toBe(`/seller-input/${SELLER_TOKEN}/submitted`);
  });

  it('이미 제출된 세션이면 다시 제출할 수 없다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: true }),
    );

    renderAt(`/seller-input/${SELLER_TOKEN}`);

    expect(await screen.findByText(/이미 제출/)).toBeInTheDocument();
    expect(screen.queryByLabelText('판매자 이름')).not.toBeInTheDocument();
  });

  it('세션 조회 실패를 오류로 보여준다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(404, { message: '판매자 입력 세션을 찾을 수 없습니다.' }),
    );

    renderAt(`/seller-input/${SELLER_TOKEN}`);

    expect(
      await screen.findByText('판매자 입력 세션을 찾을 수 없습니다.'),
    ).toBeInTheDocument();
  });
});

describe('최종 금액 확인 화면', () => {
  it('상태만 조회하고 동의·거절 action은 server 계약이 없어 비활성이다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(200, {
        status: 'FINAL_AMOUNT_CONFIRM_PENDING',
        buyerName: '가상구매자',
        buyerPhoneNumber: '01000000000',
        deliveryAddress: '가상시 가상구 가상로 1',
        itemType: '3인용 소파',
        highValueItem: true,
        sellerInputCompleted: true,
        createdAt: '2026-08-06T10:00:00',
      }),
    );

    renderAt(`/final-amount/${BUYER_TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: '최종 금액 확인이 필요해요' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '진행하기' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '거래 취소' })).toBeDisabled();

    // 구매자 화면에 판매자 정보를 노출하지 않는다.
    expect(screen.queryByText(/010-/)).not.toBeInTheDocument();
  });
});
