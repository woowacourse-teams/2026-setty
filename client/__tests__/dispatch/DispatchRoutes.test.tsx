import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useRoutes } from 'react-router-dom';
import { dispatchRoutes } from '@/app/routes/dispatchRoutes';
import { API_ORIGIN } from '@/shared/api/http';
import { completeOnboarding } from '@/app/onboarding/onboardingStorage';

/**
 * 배차 flow의 화면 전환과 server 계약 연결을 확인한다.
 * 화면 전환은 URL로 표현하므로 진입도 경로로 한다.
 * 개인정보를 남기지 않도록 명백한 가상 데이터만 쓴다.
 */

const PRIVACY_CONSENT_NAME = '(필수) 개인정보 수집·이용 동의';
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

/** jsdom에는 objectURL 구현이 없어 미리보기 URL을 고정값으로 대신한다. */
const PREVIEW_URL = 'blob:preview-url';
const createObjectURL = jest.fn(() => PREVIEW_URL);
const revokeObjectURL = jest.fn();

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
  // 홈 첫 진입 온보딩은 이 테스트들의 대상이 아니므로 이미 본 기기로 둔다.
  completeOnboarding();
  mockFetch.mockReset();
  global.fetch = mockFetch as unknown as typeof fetch;

  createObjectURL.mockClear();
  revokeObjectURL.mockClear();
  URL.createObjectURL = createObjectURL as unknown as typeof URL.createObjectURL;
  URL.revokeObjectURL = revokeObjectURL as unknown as typeof URL.revokeObjectURL;
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

    expect(screen.getByRole('heading', { name: /번거로운 중고 가구 거래/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '거래 링크 만들기' }));
    expect(currentPath()).toBe('/dispatch/new');

    await user.type(screen.getByLabelText('상품명'), '3인용 소파');
    await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
    await user.type(screen.getByLabelText('연락처'), '01000000000');
    await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
    await user.type(
      screen.getByLabelText('당근 게시물 링크'),
      'https://www.daangn.com/articles/00000000',
    );
    await user.click(screen.getByRole('button', { name: /50만원 이상/ }));
    await user.click(screen.getByRole('checkbox', { name: PRIVACY_CONSENT_NAME }));
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
      productLink: 'https://www.daangn.com/articles/00000000',
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
    await user.type(
      screen.getByLabelText('당근 게시물 링크'),
      'https://www.daangn.com/articles/00000000',
    );
    await user.click(screen.getByRole('checkbox', { name: PRIVACY_CONSENT_NAME }));
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(
      await screen.findByText('입력값이 올바르지 않습니다: buyerPhoneNumber'),
    ).toBeInTheDocument();
    expect(screen.queryByText('거래가 시작됐어요')).not.toBeInTheDocument();
    expect(currentPath()).toBe('/dispatch/new');
  });

  it('당근 게시물 링크를 입력하지 않으면 API를 호출하지 않는다', async () => {
    const user = userEvent.setup();

    renderAt('/dispatch/new');

    await user.type(screen.getByLabelText('상품명'), '3인용 소파');
    await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
    await user.type(screen.getByLabelText('연락처'), '01000000000');
    await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
    await user.click(screen.getByRole('checkbox', { name: PRIVACY_CONSENT_NAME }));
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(screen.getByText('당근 게시물 링크를 입력해 주세요.')).toBeInTheDocument();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('개인정보 수집·이용에 동의하지 않으면 API를 호출하지 않는다', async () => {
    const user = userEvent.setup();

    renderAt('/dispatch/new');

    await user.type(screen.getByLabelText('상품명'), '3인용 소파');
    await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
    await user.type(screen.getByLabelText('연락처'), '01000000000');
    await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
    await user.type(
      screen.getByLabelText('당근 게시물 링크'),
      'https://www.daangn.com/articles/00000000',
    );
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(screen.getByText('개인정보 수집·이용에 동의해 주세요.')).toBeInTheDocument();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('보기로 연 동의 화면에서 동의하면 입력값을 유지한 채 체크된다', async () => {
    const user = userEvent.setup();

    renderAt('/dispatch/new');
    await user.type(screen.getByLabelText('상품명'), '3인용 소파');

    await user.click(screen.getByRole('button', { name: '보기' }));

    expect(screen.getByRole('heading', { name: /아래 정보를 수집해요/ })).toBeInTheDocument();
    expect(screen.getByText('이름, 연락처, 받는 주소, 거래 정보')).toBeInTheDocument();
    expect(currentPath()).toBe('/dispatch/new');

    await user.click(screen.getByRole('button', { name: '동의하고 계속하기' }));

    expect(screen.getByRole('checkbox', { name: PRIVACY_CONSENT_NAME })).toBeChecked();
    expect(screen.getByLabelText('상품명')).toHaveValue('3인용 소파');
  });

  it('연락처 형식이 server @Pattern과 다르면 API를 호출하지 않는다', async () => {
    const user = userEvent.setup();

    renderAt('/dispatch/new');

    await user.type(screen.getByLabelText('상품명'), '3인용 소파');
    await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
    await user.type(screen.getByLabelText('연락처'), '123');
    await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
    await user.type(
      screen.getByLabelText('당근 게시물 링크'),
      'https://www.daangn.com/articles/00000000',
    );
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    await waitFor(() => expect(screen.getByLabelText('연락처')).toBeInvalid());
    expect(mockFetch).not.toHaveBeenCalled();
  });
});

describe('구매자 물품 사진 첨부', () => {
  const ITEM_IMAGE_URL = `${API_ORIGIN}/setty/images/items/item-test.png`;

  const fillRequiredFields = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.type(screen.getByLabelText('상품명'), '3인용 소파');
    await user.type(screen.getByLabelText('구매자 이름'), '가상구매자');
    await user.type(screen.getByLabelText('연락처'), '01000000000');
    await user.type(screen.getByLabelText('받는 주소'), '가상시 가상구 가상로 1');
    await user.type(
      screen.getByLabelText('당근 게시물 링크'),
      'https://www.daangn.com/articles/00000000',
    );
    await user.click(screen.getByRole('checkbox', { name: PRIVACY_CONSENT_NAME }));
  };

  const attachImage = (user: ReturnType<typeof userEvent.setup>) =>
    user.upload(
      screen.getByLabelText('물품 상태 사진'),
      new File(['가상이미지'], 'item.png', { type: 'image/png' }),
    );

  const requestsTo = (path: string) =>
    mockFetch.mock.calls.filter(([url]) => url === `${API_ORIGIN}${path}`);

  it('첨부한 사진을 먼저 올리고 받은 URL을 itemImageUrls로 함께 보낸다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(jsonResponse(201, { imageUrl: ITEM_IMAGE_URL }))
      .mockResolvedValueOnce(
        jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
      );

    renderAt('/dispatch/new');
    await fillRequiredFields(user);
    await attachImage(user);
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();

    const [uploadUrl, uploadInit] = mockFetch.mock.calls[0];
    expect(uploadUrl).toBe(`${API_ORIGIN}/api/dispatch-requests/images`);
    expect(uploadInit?.method).toBe('POST');
    // multipart는 브라우저가 boundary를 붙여야 하므로 Content-Type을 지정하지 않는다.
    expect(uploadInit?.headers).toBeUndefined();
    expect((uploadInit?.body as FormData).get('image')).toBeInstanceOf(File);

    const { url, init } = lastRequest();
    expect(url).toBe(`${API_ORIGIN}/api/dispatch-requests`);
    expect(JSON.parse(String(init?.body)).itemImageUrls).toEqual([ITEM_IMAGE_URL]);
  });

  it('사진을 첨부하지 않으면 업로드를 호출하지 않고 itemImageUrls도 보내지 않는다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
    );

    renderAt('/dispatch/new');
    await fillRequiredFields(user);
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();
    expect(requestsTo('/api/dispatch-requests/images')).toHaveLength(0);

    const { init } = lastRequest();
    expect(JSON.parse(String(init?.body))).not.toHaveProperty('itemImageUrls');
  });

  it('이미지가 아닌 파일은 첨부되지 않고 업로드도 하지 않는다', async () => {
    // accept 필터를 지나쳐 온 파일도 화면이 다시 확인하는지 본다.
    const user = userEvent.setup({ applyAccept: false });

    renderAt('/dispatch/new');
    await user.upload(
      screen.getByLabelText('물품 상태 사진'),
      new File(['가상문서'], 'item.pdf', { type: 'application/pdf' }),
    );

    expect(await screen.findByText('이미지 파일만 첨부할 수 있어요.')).toBeInTheDocument();
    expect(screen.queryByAltText('첨부한 물품 상태 사진')).not.toBeInTheDocument();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('업로드가 실패하면 server 문구를 보여주고 배차 요청을 만들지 않는다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(400, { message: '물품 사진은 10MB 이하만 올릴 수 있습니다.' }),
    );

    renderAt('/dispatch/new');
    await fillRequiredFields(user);
    await attachImage(user);
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(
      await screen.findByText('물품 사진은 10MB 이하만 올릴 수 있습니다.'),
    ).toBeInTheDocument();
    expect(requestsTo('/api/dispatch-requests')).toHaveLength(0);
    expect(currentPath()).toBe('/dispatch/new');
  });

  it('배차 요청 생성이 실패해 다시 제출해도 같은 사진을 두 번 올리지 않는다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(jsonResponse(201, { imageUrl: ITEM_IMAGE_URL }))
      .mockResolvedValueOnce(jsonResponse(500, { message: '요청을 처리하지 못했습니다.' }))
      .mockResolvedValueOnce(
        jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
      );

    renderAt('/dispatch/new');
    await fillRequiredFields(user);
    await attachImage(user);
    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(await screen.findByText('요청을 처리하지 못했습니다.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();
    expect(requestsTo('/api/dispatch-requests/images')).toHaveLength(1);
    expect(JSON.parse(String(lastRequest().init?.body)).itemImageUrls).toEqual([
      ITEM_IMAGE_URL,
    ]);
  });

  it('첨부한 사진을 삭제하면 업로드 없이 제출된다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(201, { buyerToken: BUYER_TOKEN, sellerInputUrl: SELLER_INPUT_URL }),
    );

    renderAt('/dispatch/new');
    await fillRequiredFields(user);
    await attachImage(user);

    expect(await screen.findByAltText('첨부한 물품 상태 사진')).toHaveAttribute(
      'src',
      PREVIEW_URL,
    );

    await user.click(screen.getByRole('button', { name: '사진 삭제' }));
    expect(revokeObjectURL).toHaveBeenCalledWith(PREVIEW_URL);

    await user.click(screen.getByRole('button', { name: '링크 생성하기' }));

    expect(await screen.findByText('거래가 시작됐어요')).toBeInTheDocument();
    expect(requestsTo('/api/dispatch-requests/images')).toHaveLength(0);
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

  it('시안대로 공유·복사 두 버튼만 두고 실패 전에는 대체 동선을 두지 않는다', async () => {
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
    expect(screen.getByRole('button', { name: '안내문과 링크 공유하기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '안내문과 링크 복사' })).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '판매자 입력 상태 확인하기' }),
    ).not.toBeInTheDocument();
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
  /** 세션 조회 응답을 받고 입력 가능한 상태까지 기다린다. */
  const openSellerForm = async () => {
    renderAt(`/seller-input/${SELLER_TOKEN}/form`);

    return screen.findByLabelText('판매자 이름');
  };

  const fillSellerFields = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.type(screen.getByLabelText('판매자 이름'), '가상판매자');
    await user.type(screen.getByLabelText('연락처'), '01000000001');
    await user.type(screen.getByLabelText('발송 주소'), '가상시 가상구 가상로 2');
    await user.type(screen.getByLabelText('회수 희망 시간'), '평일 오후 2시 이후');
  };

  it('판매자 링크로 들어오면 소개 화면을 먼저 보여주고 세션을 조회하지 않는다', async () => {
    renderAt(`/seller-input/${SELLER_TOKEN}`);

    expect(
      screen.getByRole('heading', { name: /SETTY\s*로 거래가 요청됐어요/ }),
    ).toBeInTheDocument();
    expect(screen.getByText('결제나 계좌 정보는 묻지 않아요')).toBeInTheDocument();
    expect(screen.queryByLabelText('판매자 이름')).not.toBeInTheDocument();
    // 소개만 보고 나가는 판매자에게는 세션 조회 요청도 나가지 않는다.
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('소개 화면에서 진행하기를 누르면 입력 폼으로 이동한다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: false }),
    );

    renderAt(`/seller-input/${SELLER_TOKEN}`);

    await user.click(screen.getByRole('button', { name: '진행하기' }));

    expect(currentPath()).toBe(`/seller-input/${SELLER_TOKEN}/form`);
    expect(await screen.findByLabelText('판매자 이름')).toBeInTheDocument();
  });

  it('입력 폼에서 뒤로가기를 누르면 소개 화면으로 돌아온다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: false }),
    );

    renderAt(`/seller-input/${SELLER_TOKEN}`);
    await user.click(screen.getByRole('button', { name: '진행하기' }));
    await screen.findByLabelText('판매자 이름');

    await user.click(screen.getByRole('button', { name: '뒤로 가기' }));

    expect(currentPath()).toBe(`/seller-input/${SELLER_TOKEN}`);
    expect(
      screen.getByRole('heading', { name: /SETTY\s*로 거래가 요청됐어요/ }),
    ).toBeInTheDocument();
  });

  it('판매자 링크로 들어오면 세션을 조회하고 입력을 제출한다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: false }),
      )
      .mockResolvedValueOnce(emptyResponse(204));

    renderAt(`/seller-input/${SELLER_TOKEN}/form`);

    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith(
        `${API_ORIGIN}/api/dispatch-requests/seller-sessions/${SELLER_TOKEN}`,
        expect.objectContaining({ method: 'GET' }),
      ),
    );
    expect(await screen.findByText('3인용 소파')).toBeInTheDocument();

    await fillSellerFields(user);
    await user.click(screen.getByRole('checkbox', { name: PRIVACY_CONSENT_NAME }));
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

    renderAt(`/seller-input/${SELLER_TOKEN}/form`);

    expect(await screen.findByText(/이미 제출/)).toBeInTheDocument();
    expect(screen.queryByLabelText('판매자 이름')).not.toBeInTheDocument();
  });

  it('물품 상태 사진은 구매자만 첨부하므로 판매자 화면에는 없다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: false }),
    );

    await openSellerForm();

    expect(screen.queryByLabelText('물품 상태 사진')).not.toBeInTheDocument();
    expect(screen.queryByText('업로드')).not.toBeInTheDocument();
  });

  it('개인정보 수집·이용에 동의하지 않으면 제출 API를 호출하지 않는다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: false }),
    );

    await openSellerForm();

    await fillSellerFields(user);
    await user.click(screen.getByRole('button', { name: '제출하기' }));

    expect(screen.getByText('개인정보 수집·이용에 동의해 주세요.')).toBeInTheDocument();
    // 세션 조회 1회만 남고 제출 요청은 나가지 않는다.
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('보기로 연 동의 화면에서 동의하면 입력값을 유지한 채 체크된다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(
      jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: false }),
    );

    await openSellerForm();

    await user.type(screen.getByLabelText('판매자 이름'), '가상판매자');

    await user.click(screen.getByRole('button', { name: '보기' }));

    expect(screen.getByRole('heading', { name: /아래 정보를 수집해요/ })).toBeInTheDocument();
    expect(currentPath()).toBe(`/seller-input/${SELLER_TOKEN}/form`);

    await user.click(screen.getByRole('button', { name: '동의하고 계속하기' }));

    expect(screen.getByRole('checkbox', { name: PRIVACY_CONSENT_NAME })).toBeChecked();
    expect(screen.getByLabelText('판매자 이름')).toHaveValue('가상판매자');
  });

  it('이미 제출된 세션에는 동의 항목을 보여주지 않는다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(200, { itemType: '3인용 소파', alreadySubmitted: true }),
    );

    renderAt(`/seller-input/${SELLER_TOKEN}/form`);

    expect(await screen.findByText(/이미 제출/)).toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', { name: PRIVACY_CONSENT_NAME }),
    ).not.toBeInTheDocument();
  });

  it('세션 조회 실패를 오류로 보여준다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(404, { message: '판매자 입력 세션을 찾을 수 없습니다.' }),
    );

    renderAt(`/seller-input/${SELLER_TOKEN}/form`);

    expect(
      await screen.findByText('판매자 입력 세션을 찾을 수 없습니다.'),
    ).toBeInTheDocument();
  });
});

describe('최종 금액 확인 화면', () => {
  const buyerRequest = (overrides: Record<string, unknown>) => ({
    status: 'FINAL_AMOUNT_CONFIRM_PENDING',
    buyerName: '가상구매자',
    buyerPhoneNumber: '010-0000-0000',
    deliveryAddress: '가상시 가상구 가상로 1',
    itemType: '3인용 소파',
    highValueItem: true,
    sellerInputCompleted: true,
    createdAt: '2026-08-06T10:00:00+09:00',
    sellerInputUrl: SELLER_INPUT_URL,
    finalQuotedAmount: 9900,
    ...overrides,
  });

  it('운영자가 기록한 최종 금액을 보여주고 진행하기로 동의를 보낸다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(jsonResponse(200, buyerRequest({})))
      .mockResolvedValueOnce(emptyResponse(204))
      .mockResolvedValueOnce(
        jsonResponse(200, buyerRequest({ status: 'DISPATCH_PENDING' })),
      );

    renderAt(`/final-amount/${BUYER_TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: /9,900원\s*이면 배달돼요/ }),
    ).toBeInTheDocument();
    expect(screen.getByText('진행하면 안전하게 배송이 시작돼요.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '진행하기' }));

    // 되돌릴 수 없는 동의라 확인 시트를 거친 뒤에만 요청이 나간다.
    const sheet = await screen.findByRole('dialog', { name: '이대로 진행할까요?' });
    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(within(sheet).getByText(/9,900원/)).toBeInTheDocument();

    await user.click(within(sheet).getByRole('button', { name: '네, 진행할게요' }));

    await waitFor(() =>
      expect(lastRequest().url).toBe(
        `${API_ORIGIN}/api/dispatch-requests/${BUYER_TOKEN}`,
      ),
    );
    expect(mockFetch.mock.calls[1][0]).toBe(
      `${API_ORIGIN}/api/dispatch-requests/${BUYER_TOKEN}/approval`,
    );
    expect(mockFetch.mock.calls[1][1]?.method).toBe('POST');

    expect(
      await screen.findByRole('heading', { name: '진행하기로 확인했어요' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '진행하기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('확인 시트를 닫으면 동의 요청을 보내지 않는다', async () => {
    const user = userEvent.setup();
    mockFetch.mockResolvedValue(jsonResponse(200, buyerRequest({})));

    renderAt(`/final-amount/${BUYER_TOKEN}`);

    await user.click(await screen.findByRole('button', { name: '진행하기' }));
    await user.click(screen.getByRole('button', { name: '다시 볼게요' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    // 조회 1회만 남고 approval은 나가지 않는다.
    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(
      screen.getByRole('heading', { name: /9,900원\s*이면 배달돼요/ }),
    ).toBeInTheDocument();

    // Esc로 닫아도 같다.
    await user.click(screen.getByRole('button', { name: '진행하기' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('금액을 확인하는 동안에는 시안의 두 action만 보여준다', async () => {
    mockFetch.mockResolvedValue(jsonResponse(200, buyerRequest({})));

    renderAt(`/final-amount/${BUYER_TOKEN}`);

    // 거래 취소는 연결할 server 계약이 없어 비활성이다.
    expect(await screen.findByRole('button', { name: '거래 취소' })).toBeDisabled();
    expect(screen.getAllByRole('button')).toHaveLength(2);
    expect(
      screen.queryByRole('button', { name: '홈으로 돌아가기' }),
    ).not.toBeInTheDocument();
  });

  it('이미 종료된 요청에는 동의 action을 보여주지 않는다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(200, buyerRequest({ status: 'FINAL_AMOUNT_REJECTED' })),
    );

    renderAt(`/final-amount/${BUYER_TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: '진행이 종료됐어요' }),
    ).toBeInTheDocument();
    expect(screen.getByText('최종 금액을 거절해 종료됐어요')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '진행하기' })).not.toBeInTheDocument();
  });

  it('운영자가 안내하는 구매자 상태 링크에서 최종 금액 화면으로 이동한다', async () => {
    mockFetch.mockResolvedValue(jsonResponse(200, buyerRequest({})));

    renderAt(`/dispatch/${BUYER_TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: /9,900원\s*이면 배달돼요/ }),
    ).toBeInTheDocument();
    expect(currentPath()).toBe(`/final-amount/${BUYER_TOKEN}`);
  });

  it('최종 금액이 아직 없으면 동의·취소 action을 보여주지 않는다', async () => {
    mockFetch.mockResolvedValue(
      jsonResponse(
        200,
        buyerRequest({ status: 'FINAL_REVIEW_PENDING', finalQuotedAmount: null }),
      ),
    );

    renderAt(`/final-amount/${BUYER_TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: '최종 금액을 확인하고 있어요' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '진행하기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '거래 취소' })).not.toBeInTheDocument();

    // 구매자 화면에 판매자 정보를 노출하지 않는다.
    expect(screen.queryByText(/010-/)).not.toBeInTheDocument();
  });

  it('동의 요청이 실패하면 오류를 그대로 보여주고 화면을 넘기지 않는다', async () => {
    const user = userEvent.setup();
    mockFetch
      .mockResolvedValueOnce(jsonResponse(200, buyerRequest({})))
      .mockResolvedValueOnce(
        jsonResponse(409, { message: '이미 처리된 요청이에요.' }),
      );

    renderAt(`/final-amount/${BUYER_TOKEN}`);

    await user.click(await screen.findByRole('button', { name: '진행하기' }));
    await user.click(await screen.findByRole('button', { name: '네, 진행할게요' }));

    // 실패는 시트 안에서 알리고 그 자리에서 다시 시도할 수 있어야 한다.
    const sheet = await screen.findByRole('dialog');
    expect(within(sheet).getByText('이미 처리된 요청이에요.')).toBeInTheDocument();
    expect(within(sheet).getByRole('button', { name: '네, 진행할게요' })).toBeEnabled();
    expect(
      screen.getByRole('heading', { name: /9,900원\s*이면 배달돼요/ }),
    ).toBeInTheDocument();
  });
});
