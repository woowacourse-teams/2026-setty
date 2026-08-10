import { beforeEach, expect, jest, test } from '@jest/globals';
import '@testing-library/jest-dom/jest-globals';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useRoutes } from 'react-router-dom';
import {
  clearOperatorSecret,
  getOperatorSecret,
  storeOperatorSecret,
} from '../auth/operatorSecretStorage';
import { operatorRoutes } from '../routes';

const fetchMock = jest.fn<typeof fetch>();
const clipboardWriteTextMock = jest.fn<(value: string) => Promise<void>>();
const TEST_OPERATOR_SECRET = 'FAKE_OPERATOR_SECRET_FOR_TESTS';
const FAKE_SELLER_INPUT_URL = 'https://example.test/seller-input/FAKE_SELLER_INPUT_TOKEN';
const FAKE_BUYER_CONFIRM_URL = 'https://example.test/dispatch/FAKE_BUYER_TOKEN';

const PENDING_DETAIL = {
  id: 28,
  status: 'SELLER_INPUT_PENDING',
  itemType: '테스트 소파',
  highValueItem: false,
  estimateRequestId: null,
  createdAt: '2026-08-07T10:00:00+09:00',
  buyer: {
    name: '가상구매자',
    phoneNumber: '010-0000-0000',
    deliveryAddress: '테스트시 수령로 10',
  },
  seller: null,
  sellerInputUrl: FAKE_SELLER_INPUT_URL,
  sellerInputCompletedAt: null,
  finalQuotedAmount: null,
  messageContent: null,
  buyerConfirmUrl: null,
  amountCheckedAt: null,
  operatorNote: null,
  closedReason: null,
};

const COMPLETED_DETAIL = {
  ...PENDING_DETAIL,
  status: 'DISPATCH_COMPLETED',
  estimateRequestId: 12,
  seller: {
    name: '가상판매자',
    phoneNumber: '010-1111-0000',
    pickupAddress: '테스트시 픽업로 20',
    availablePickupTime: '평일 오후 2시 이후',
  },
  sellerInputCompletedAt: '2026-08-07T10:30:00+09:00',
  finalQuotedAmount: 45000,
  messageContent: '최종 운송비는 45,000원인 가상 안내입니다.',
  buyerConfirmUrl: FAKE_BUYER_CONFIRM_URL,
  amountCheckedAt: '2026-08-07T11:00:00+09:00',
  operatorNote: '가상 운영 메모',
  closedReason: '가상 종료 사유',
};

const FINAL_REVIEW_DETAIL = {
  ...COMPLETED_DETAIL,
  status: 'FINAL_REVIEW_PENDING',
  finalQuotedAmount: null,
  messageContent: null,
  buyerConfirmUrl: null,
  amountCheckedAt: null,
  operatorNote: null,
  closedReason: null,
};

const FINAL_AMOUNT_CONFIRM_DETAIL = {
  ...FINAL_REVIEW_DETAIL,
  status: 'FINAL_AMOUNT_CONFIRM_PENDING',
  finalQuotedAmount: 45000,
  messageContent: '최종 운송비는 45,000원인 가상 안내입니다.',
  buyerConfirmUrl: FAKE_BUYER_CONFIRM_URL,
};

const DISPATCH_PENDING_DETAIL = {
  ...FINAL_AMOUNT_CONFIRM_DETAIL,
  status: 'DISPATCH_PENDING',
  amountCheckedAt: '2026-08-07T11:00:00+09:00',
};

function response(body?: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string) =>
        name.toLowerCase() === 'content-type' && body !== undefined
          ? 'application/json'
          : null,
    } as Headers,
    json: async () => body,
  } as unknown as Response;
}

function OperatorTestRoutes() {
  return useRoutes(operatorRoutes);
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <OperatorTestRoutes />
    </MemoryRouter>,
  );
}

function authenticateTestOperator() {
  storeOperatorSecret(TEST_OPERATOR_SECRET);
}

function expectOperatorSecretHeader(callIndex: number) {
  const requestInit = fetchMock.mock.calls[callIndex]?.[1];
  const headers = requestInit?.headers;
  expect(headers).toBeInstanceOf(Headers);
  expect((headers as Headers).get('X-Operator-Secret')).toBe(TEST_OPERATOR_SECRET);
}

beforeEach(() => {
  fetchMock.mockReset();
  clipboardWriteTextMock.mockReset();
  clipboardWriteTextMock.mockResolvedValue(undefined);
  clearOperatorSecret();
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    value: fetchMock,
    writable: true,
  });
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: clipboardWriteTextMock },
  });
});

test('미인증 운영자의 배차 직접 URL 접근을 로그인 화면으로 보낸다', async () => {
  renderAt('/operator/dispatch-requests');

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
});

test('로그인하면 원래 배차 URL로 돌아가고 공통 인증 API를 사용한다', async () => {
  fetchMock
    .mockResolvedValueOnce(response({ authenticated: true }))
    .mockResolvedValueOnce(response([]));
  renderAt('/operator/dispatch-requests');

  const user = userEvent.setup();
  await user.type(screen.getByLabelText('비밀번호'), TEST_OPERATOR_SECRET);
  await user.click(screen.getByRole('button', { name: '운영자 페이지 들어가기' }));

  expect(
    await screen.findByRole('heading', { name: '조건에 맞는 배차 요청이 없어요' }),
  ).toBeInTheDocument();
  expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/operator/auth');
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/dispatch-requests',
  );
  expectOperatorSecretHeader(0);
  expectOperatorSecretHeader(1);
});

test('배차 목록을 응답 순서대로 표시하고 운영자 메뉴에서 현재 위치를 알린다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(
    response([
      {
        id: 29,
        status: 'FINAL_REVIEW_PENDING',
        itemType: '테스트 테이블',
        highValueItem: true,
        sellerInputCompleted: true,
        finalQuotedAmount: 45000,
        createdAt: '2026-08-07T11:00:00+09:00',
      },
      {
        id: 28,
        status: 'SELLER_INPUT_PENDING',
        itemType: '테스트 소파',
        highValueItem: false,
        sellerInputCompleted: false,
        finalQuotedAmount: null,
        createdAt: '2026-08-07T10:00:00+09:00',
      },
    ]),
  );
  renderAt('/operator/dispatch-requests');

  const requestLinks = await screen.findAllByRole('link', { name: /#2[89]/ });
  expect(requestLinks.map((link) => link.textContent)).toEqual(['#29', '#28']);
  expect(screen.getByText('45,000원')).toBeInTheDocument();
  expect(screen.getByText('미기록')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '배차 요청' })).toHaveAttribute(
    'aria-current',
    'page',
  );
  expect(screen.getByRole('link', { name: '견적 요청' })).not.toHaveAttribute(
    'aria-current',
  );
  expectOperatorSecretHeader(0);
});

test('배차 목록의 ID가 아닌 셀을 클릭해도 상세로 이동한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(
      response([
        {
          id: 28,
          status: 'SELLER_INPUT_PENDING',
          itemType: '테스트 소파',
          highValueItem: false,
          sellerInputCompleted: false,
          finalQuotedAmount: null,
          createdAt: '2026-08-07T10:00:00+09:00',
        },
      ]),
    )
    .mockResolvedValueOnce(response(PENDING_DETAIL));
  renderAt('/operator/dispatch-requests');

  await userEvent.click(await screen.findByText('테스트 소파'));

  expect(
    await screen.findByRole('heading', { name: '배차 요청 상세' }),
  ).toBeInTheDocument();
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/dispatch-requests/28',
  );
  expectOperatorSecretHeader(0);
  expectOperatorSecretHeader(1);
});

test('상태 필터를 URL과 API query에 반영한다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response([])).mockResolvedValueOnce(response([]));
  renderAt('/operator/dispatch-requests');

  await screen.findByRole('heading', { name: '조건에 맞는 배차 요청이 없어요' });
  await userEvent.selectOptions(screen.getByLabelText('상태'), 'SELLER_INPUT_PENDING');

  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/dispatch-requests?status=SELLER_INPUT_PENDING',
  );
  expect(screen.getByLabelText('상태')).toHaveValue('SELLER_INPUT_PENDING');
});

test('배차 목록 조회 실패 후 다시 시도해 빈 상태를 확인한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response({ message: '가상 서버 오류' }, 500))
    .mockResolvedValueOnce(response([]));
  renderAt('/operator/dispatch-requests');

  expect(
    await screen.findByRole('heading', {
      name: '배차 요청 목록을 불러오지 못했어요',
    }),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));

  expect(
    await screen.findByRole('heading', { name: '조건에 맞는 배차 요청이 없어요' }),
  ).toBeInTheDocument();
});

test('판매자 미입력 상세에서 대기 상태와 판매자 링크 복사를 제공한다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response(PENDING_DETAIL));
  renderAt('/operator/dispatch-requests/28');

  expect(
    await screen.findByRole('heading', { name: '배차 요청 상세' }),
  ).toBeInTheDocument();
  expect(
    screen.getByText('판매자 입력 대기', { selector: 'strong' }),
  ).toBeInTheDocument();
  expect(screen.getByLabelText('판매자 입력 링크')).toHaveValue(FAKE_SELLER_INPUT_URL);

  await userEvent.click(screen.getByRole('button', { name: '판매자 입력 링크 복사' }));

  expect(clipboardWriteTextMock).toHaveBeenCalledWith(FAKE_SELLER_INPUT_URL);
  expect(await screen.findByRole('status')).toHaveTextContent(
    '판매자 입력 링크를 복사했습니다.',
  );
  expect(screen.queryByRole('textbox', { name: '운영 메모' })).not.toBeInTheDocument();
});

test('링크 복사 실패를 성공으로 표시하지 않는다', async () => {
  authenticateTestOperator();
  clipboardWriteTextMock.mockRejectedValueOnce(new Error('FAKE_CLIPBOARD_ERROR'));
  fetchMock.mockResolvedValueOnce(response(PENDING_DETAIL));
  renderAt('/operator/dispatch-requests/28');

  await screen.findByRole('heading', { name: '배차 요청 상세' });
  await userEvent.click(screen.getByRole('button', { name: '판매자 입력 링크 복사' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '링크를 복사하지 못했습니다.',
  );
  expect(
    screen.getByRole('button', { name: '판매자 입력 링크 복사' }),
  ).toBeInTheDocument();
});

test('배차 완료 요청의 금액은 읽기 전용이고 메시지는 계속 수정할 수 있다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response(COMPLETED_DETAIL));
  renderAt('/operator/dispatch-requests/28');

  expect(
    await screen.findByRole('heading', { name: '배차 요청 상세' }),
  ).toBeInTheDocument();
  expect(screen.getByText('가상구매자')).toBeInTheDocument();
  expect(screen.getByText('가상판매자')).toBeInTheDocument();
  expect(screen.getByText('테스트시 수령로 10')).toBeInTheDocument();
  expect(screen.getByText('테스트시 픽업로 20')).toBeInTheDocument();
  expect(screen.getByText('45,000원')).toBeInTheDocument();
  expect(screen.getByLabelText('문자 안내 및 상황 기록')).toHaveValue(
    '최종 운송비는 45,000원인 가상 안내입니다.',
  );
  expect(screen.getByRole('button', { name: '메시지 수정' })).toBeInTheDocument();
  expect(screen.getByText('가상 운영 메모')).toBeInTheDocument();
  expect(screen.getByText('가상 종료 사유')).toBeInTheDocument();
  expect(screen.getByLabelText('구매자 최종 승인 링크')).toHaveValue(
    FAKE_BUYER_CONFIRM_URL,
  );
  expect(screen.getByRole('link', { name: '#12' })).toHaveAttribute(
    'href',
    '/operator/estimate-requests/12',
  );
  expect(screen.queryByRole('spinbutton', { name: '최종 금액' })).not.toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: /상태 변경|삭제/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: '배차 완료로 변경' }),
  ).not.toBeInTheDocument();
});

test('최종 금액만 저장하고 구매자 승인 링크를 복사한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(FINAL_REVIEW_DETAIL))
    .mockResolvedValueOnce(response({ buyerConfirmUrl: FAKE_BUYER_CONFIRM_URL }));
  renderAt('/operator/dispatch-requests/28');

  expect(
    await screen.findByRole('heading', { name: '배차 요청 상세' }),
  ).toBeInTheDocument();
  const user = userEvent.setup();
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: clipboardWriteTextMock },
  });
  await user.type(screen.getByLabelText('최종 금액'), '30000');
  await user.click(screen.getByRole('button', { name: '최종 금액 저장' }));

  expect(await screen.findByText('최종 금액을 저장했습니다.')).toBeInTheDocument();
  expect(screen.getByText('최종 금액 확인 대기')).toBeInTheDocument();
  expect(screen.getByLabelText('최종 금액')).toHaveValue(30000);
  expect(screen.getByLabelText('문자 안내 및 상황 기록')).toHaveValue('');
  const buyerConfirmInput = screen.getByLabelText('구매자 최종 승인 링크');
  expect(buyerConfirmInput).toHaveValue(FAKE_BUYER_CONFIRM_URL);
  await user.click(
    within(buyerConfirmInput.parentElement as HTMLElement).getByRole('button', {
      name: '구매자 최종 승인 링크 복사',
    }),
  );
  await waitFor(() =>
    expect(clipboardWriteTextMock).toHaveBeenCalledWith(FAKE_BUYER_CONFIRM_URL),
  );
  expect(
    await screen.findByText('구매자 최종 승인 링크를 복사했습니다.'),
  ).toBeInTheDocument();

  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/dispatch-requests/28/final-amount',
  );
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        finalQuotedAmount: 30000,
      }),
    }),
  );
  expect(fetchMock).toHaveBeenCalledTimes(2);
  expectOperatorSecretHeader(0);
  expectOperatorSecretHeader(1);
});

test('구매자 확인 대기 요청의 기존 금액을 금액 전용 PUT으로 수정한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(FINAL_AMOUNT_CONFIRM_DETAIL))
    .mockResolvedValueOnce(response({ buyerConfirmUrl: FAKE_BUYER_CONFIRM_URL }));
  renderAt('/operator/dispatch-requests/28');

  const amountInput = await screen.findByLabelText('최종 금액');
  const messageInput = screen.getByLabelText('문자 안내 및 상황 기록');
  expect(amountInput).toHaveValue(45000);
  expect(messageInput).toHaveValue('최종 운송비는 45,000원인 가상 안내입니다.');

  const user = userEvent.setup();
  await user.clear(amountInput);
  await user.type(amountInput, '50000');
  await user.click(screen.getByRole('button', { name: '최종 금액 저장' }));

  expect(await screen.findByText('최종 금액을 저장했습니다.')).toBeInTheDocument();
  expect(amountInput).toHaveValue(50000);
  expect(messageInput).toHaveValue('최종 운송비는 45,000원인 가상 안내입니다.');
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        finalQuotedAmount: 50000,
      }),
    }),
  );
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

test('최종 금액과 메시지가 비어 있으면 각 저장 요청을 보내지 않는다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response(FINAL_REVIEW_DETAIL));
  renderAt('/operator/dispatch-requests/28');

  await screen.findByRole('heading', { name: '배차 요청 상세' });
  await userEvent.click(screen.getByRole('button', { name: '최종 금액 저장' }));
  await userEvent.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(screen.getByText('최종 금액을 입력해 주세요.')).toBeInTheDocument();
  expect(screen.getByText('문자 안내 및 상황 기록을 입력해 주세요.')).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

test('최종 금액 저장 중 409가 발생하면 금액 초안을 유지한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(FINAL_AMOUNT_CONFIRM_DETAIL))
    .mockResolvedValueOnce(response({ message: '가상 상태 충돌' }, 409));
  renderAt('/operator/dispatch-requests/28');

  const amountInput = await screen.findByLabelText('최종 금액');
  const messageInput = screen.getByLabelText('문자 안내 및 상황 기록');
  const user = userEvent.setup();
  await user.clear(amountInput);
  await user.type(amountInput, '52000');
  await user.click(screen.getByRole('button', { name: '최종 금액 저장' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '요청 상태가 변경되어 최종 금액을 저장하지 못했습니다.',
  );
  expect(amountInput).toHaveValue(52000);
  expect(messageInput).toHaveValue('최종 운송비는 45,000원인 가상 안내입니다.');
});

test('최종 금액 저장 요청이 401이면 비밀번호를 삭제하고 로그인으로 이동한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(FINAL_REVIEW_DETAIL))
    .mockResolvedValueOnce(response({ message: '가상 인증 실패' }, 401));
  renderAt('/operator/dispatch-requests/28');

  await screen.findByRole('heading', { name: '배차 요청 상세' });
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('최종 금액'), '30000');
  await user.click(screen.getByRole('button', { name: '최종 금액 저장' }));

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expect(getOperatorSecret()).toBeNull();
});

test('판매자 입력 대기 상태에서도 메시지를 별도 PUT으로 저장한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response(undefined, 204));
  renderAt('/operator/dispatch-requests/28');

  const messageInput = await screen.findByLabelText('문자 안내 및 상황 기록');
  const user = userEvent.setup();
  await user.type(messageInput, '  판매자 입력을 기다리는 가상 운영 기록입니다.  ');
  await user.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(await screen.findByText('메시지 기록을 저장했습니다.')).toBeInTheDocument();
  expect(messageInput).toHaveValue('판매자 입력을 기다리는 가상 운영 기록입니다.');
  expect(screen.getByRole('button', { name: '메시지 수정' })).toBeInTheDocument();
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/dispatch-requests/28/message',
  );
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        messageContent: '판매자 입력을 기다리는 가상 운영 기록입니다.',
      }),
    }),
  );
  expect(fetchMock).toHaveBeenCalledTimes(2);
  expectOperatorSecretHeader(0);
  expectOperatorSecretHeader(1);
});

test('배차 완료 후에도 기존 메시지를 수정한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(COMPLETED_DETAIL))
    .mockResolvedValueOnce(response(undefined, 204));
  renderAt('/operator/dispatch-requests/28');

  const messageInput = await screen.findByLabelText('문자 안내 및 상황 기록');
  const user = userEvent.setup();
  await user.type(messageInput, '{enter}배차 완료 후 가상 상황을 추가했습니다.  ');
  await user.click(screen.getByRole('button', { name: '메시지 수정' }));

  expect(await screen.findByText('메시지 기록을 저장했습니다.')).toBeInTheDocument();
  expect(messageInput).toHaveValue(
    '최종 운송비는 45,000원인 가상 안내입니다.\n배차 완료 후 가상 상황을 추가했습니다.',
  );
  expect(screen.getByText('배차 완료')).toBeInTheDocument();
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/dispatch-requests/28/message',
  );
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        messageContent:
          '최종 운송비는 45,000원인 가상 안내입니다.\n배차 완료 후 가상 상황을 추가했습니다.',
      }),
    }),
  );
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

test('메시지 저장 요청이 401이면 비밀번호를 삭제하고 로그인으로 이동한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response({ message: '가상 인증 실패' }, 401));
  renderAt('/operator/dispatch-requests/28');

  await userEvent.type(
    await screen.findByLabelText('문자 안내 및 상황 기록'),
    '가상 사용자에게 보낸 테스트 문자입니다.',
  );
  await userEvent.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expect(getOperatorSecret()).toBeNull();
});

test('배차 대기 요청을 배차 완료 상태로 변경한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(DISPATCH_PENDING_DETAIL))
    .mockResolvedValueOnce(response(undefined, 204));
  renderAt('/operator/dispatch-requests/28');

  expect(
    await screen.findByRole('button', { name: '배차 완료로 변경' }),
  ).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '메시지 수정' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '배차 완료로 변경' }));

  expect(await screen.findByText('배차 완료 상태로 변경했습니다.')).toBeInTheDocument();
  expect(screen.getByText('배차 완료')).toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: '배차 완료로 변경' }),
  ).not.toBeInTheDocument();
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/dispatch-requests/28/completion',
  );
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({ method: 'POST' }),
  );
  expect(fetchMock.mock.calls[1]?.[1]?.body).toBeUndefined();
  expectOperatorSecretHeader(0);
  expectOperatorSecretHeader(1);
});

test('배차 완료 처리 중 409가 발생하면 배차 대기 상태를 유지한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(DISPATCH_PENDING_DETAIL))
    .mockResolvedValueOnce(response({ message: '가상 상태 충돌' }, 409));
  renderAt('/operator/dispatch-requests/28');

  await userEvent.click(await screen.findByRole('button', { name: '배차 완료로 변경' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '요청 상태가 변경되어 배차 완료로 처리하지 못했습니다.',
  );
  expect(screen.getByText('배차 대기')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '배차 완료로 변경' })).toBeInTheDocument();
});

test('배차 완료 요청이 401이면 비밀번호를 삭제하고 로그인으로 이동한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(DISPATCH_PENDING_DETAIL))
    .mockResolvedValueOnce(response({ message: '가상 인증 실패' }, 401));
  renderAt('/operator/dispatch-requests/28');

  await userEvent.click(await screen.findByRole('button', { name: '배차 완료로 변경' }));

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expect(getOperatorSecret()).toBeNull();
});

test('배차 상세 404를 일반 서버 오류와 구분한다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response({ message: '찾을 수 없음' }, 404));
  renderAt('/operator/dispatch-requests/999999');

  expect(
    await screen.findByRole('heading', { name: '배차 요청을 찾을 수 없어요' }),
  ).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '목록으로 돌아가기' })).toHaveAttribute(
    'href',
    '/operator/dispatch-requests',
  );
});

test('배차 API가 401을 반환하면 비밀번호를 삭제하고 로그인 화면으로 보낸다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response({ message: '인증 실패' }, 401));
  renderAt('/operator/dispatch-requests');

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expect(getOperatorSecret()).toBeNull();
});
