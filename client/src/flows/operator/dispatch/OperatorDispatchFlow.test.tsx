import { beforeEach, expect, jest, test } from '@jest/globals';
import '@testing-library/jest-dom/jest-globals';
import { render, screen, waitFor } from '@testing-library/react';
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
  amountCheckedAt: null,
  operatorNote: null,
  closedReason: null,
};

const COMPLETED_DETAIL = {
  ...PENDING_DETAIL,
  status: 'FINAL_REVIEW_PENDING',
  estimateRequestId: 12,
  seller: {
    name: '가상판매자',
    phoneNumber: '010-1111-0000',
    pickupAddress: '테스트시 픽업로 20',
    availablePickupTime: '평일 오후 2시 이후',
  },
  sellerInputCompletedAt: '2026-08-07T10:30:00+09:00',
  finalQuotedAmount: 45000,
  amountCheckedAt: '2026-08-07T11:00:00+09:00',
  operatorNote: '가상 운영 메모',
  closedReason: '가상 종료 사유',
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

  await userEvent.click(screen.getByRole('button', { name: '링크 복사' }));

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
  await userEvent.click(screen.getByRole('button', { name: '링크 복사' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '링크를 복사하지 못했습니다.',
  );
  expect(screen.getByRole('button', { name: '링크 복사' })).toBeInTheDocument();
});

test('판매자 입력과 기존 운영 기록을 상세에서 읽기 전용으로 표시한다', async () => {
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
  expect(screen.getByText('가상 운영 메모')).toBeInTheDocument();
  expect(screen.getByText('가상 종료 사유')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '#12' })).toHaveAttribute(
    'href',
    '/operator/estimate-requests/12',
  );
  expect(
    screen.queryByRole('textbox', { name: /메모|종료 사유/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: /상태 변경|삭제/ }),
  ).not.toBeInTheDocument();
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
