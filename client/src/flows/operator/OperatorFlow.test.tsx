import { beforeEach, expect, jest, test } from '@jest/globals';
import '@testing-library/jest-dom/jest-globals';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useRoutes } from 'react-router-dom';
import {
  clearOperatorSecret,
  getOperatorSecret,
  storeOperatorSecret,
} from './auth/operatorSecretStorage';
import { operatorRoutes } from './routes';

const fetchMock = jest.fn<typeof fetch>();
const TEST_OPERATOR_SECRET = 'FAKE_OPERATOR_SECRET_FOR_TESTS';
const WRONG_TEST_OPERATOR_SECRET = 'FAKE_WRONG_OPERATOR_SECRET';

const PENDING_DETAIL = {
  estimateRequestId: 12,
  name: '테스트사용자',
  phoneNumber: '01000000000',
  tradeArea: '테스트구 테스트동',
  itemType: '테스트 의자',
  highValueItem: false,
  status: 'PENDING_REVIEW',
  createdAt: '2026-08-06T10:00:00+09:00',
  manualNotification: null,
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

function expectOperatorSecretHeader(callIndex: number, secret = TEST_OPERATOR_SECRET) {
  const requestInit = fetchMock.mock.calls[callIndex]?.[1];
  const headers = requestInit?.headers;
  expect(headers).toBeInstanceOf(Headers);
  expect((headers as Headers).get('X-Operator-Secret')).toBe(secret);
  expect(requestInit).toEqual(
    expect.objectContaining({ cache: 'no-store', credentials: 'omit' }),
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  clearOperatorSecret();
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    value: fetchMock,
    writable: true,
  });
});

test('운영자 비밀번호 오류와 로그인 성공 후 빈 목록을 구분한다', async () => {
  const user = userEvent.setup();
  fetchMock
    .mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401))
    .mockResolvedValueOnce(response({ authenticated: true }))
    .mockResolvedValueOnce(response([]));
  renderAt('/operator/login');

  await user.type(screen.getByLabelText('비밀번호'), WRONG_TEST_OPERATOR_SECRET);
  await user.click(screen.getByRole('button', { name: '운영자 페이지 들어가기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(
    '비밀번호가 올바르지 않습니다.',
  );
  expect(getOperatorSecret()).toBeNull();

  await user.clear(screen.getByLabelText('비밀번호'));
  await user.type(screen.getByLabelText('비밀번호'), TEST_OPERATOR_SECRET);
  await user.click(screen.getByRole('button', { name: '운영자 페이지 들어가기' }));

  expect(
    await screen.findByRole('heading', { name: '접수된 견적 요청이 없어요' }),
  ).toBeInTheDocument();
  expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/operator/auth');
  expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/operator/auth');
  expect(fetchMock.mock.calls[2]?.[0]).toBe(
    'http://localhost:8080/api/operator/estimate-requests',
  );
  expectOperatorSecretHeader(0, WRONG_TEST_OPERATOR_SECRET);
  expectOperatorSecretHeader(1);
  expectOperatorSecretHeader(2);
  expect(getOperatorSecret()).toBe(TEST_OPERATOR_SECRET);
});

test('미인증 운영자의 직접 URL 접근을 로그인 화면으로 보낸다', async () => {
  fetchMock.mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401));

  renderAt('/operator/estimate-requests');

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
});

test('저장된 비밀번호가 401을 받으면 삭제하고 로그인 화면으로 보낸다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401));

  renderAt('/operator/estimate-requests');

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expectOperatorSecretHeader(0);
  expect(getOperatorSecret()).toBeNull();
});

test('운영자 목록에는 요약 정보만 표시하고 개인정보를 노출하지 않는다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(
    response([
      {
        estimateRequestId: 12,
        tradeArea: '테스트구 테스트동',
        itemType: '테스트 의자',
        highValueItem: false,
        status: 'PENDING_REVIEW',
        createdAt: '2026-08-06T10:00:00+09:00',
      },
    ]),
  );

  renderAt('/operator/estimate-requests');

  expect(await screen.findByRole('link', { name: '#12' })).toBeInTheDocument();
  expect(screen.getByText('테스트구 테스트동')).toBeInTheDocument();
  expect(screen.queryByText('테스트사용자')).not.toBeInTheDocument();
  expect(screen.queryByText('010-0000-0000')).not.toBeInTheDocument();
  expectOperatorSecretHeader(0);
});

test('운영자 견적 목록의 ID가 아닌 셀을 클릭해도 상세로 이동한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(
      response([
        {
          estimateRequestId: 12,
          tradeArea: '테스트구 테스트동',
          itemType: '테스트 의자',
          highValueItem: false,
          status: 'PENDING_REVIEW',
          createdAt: '2026-08-06T10:00:00+09:00',
        },
      ]),
    )
    .mockResolvedValueOnce(response(PENDING_DETAIL));
  renderAt('/operator/estimate-requests');

  await userEvent.click(await screen.findByText('테스트 의자'));

  expect(
    await screen.findByRole('heading', { name: '견적 요청 상세' }),
  ).toBeInTheDocument();
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/estimate-requests/12',
  );
  expectOperatorSecretHeader(0);
  expectOperatorSecretHeader(1);
});

test('운영자 목록 조회 실패 후 다시 시도해 빈 상태를 확인한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response({ code: 'INTERNAL_SERVER_ERROR' }, 500))
    .mockResolvedValueOnce(response([]));
  renderAt('/operator/estimate-requests');

  expect(
    await screen.findByRole('heading', { name: '목록을 불러오지 못했어요' }),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));

  expect(
    await screen.findByRole('heading', { name: '접수된 견적 요청이 없어요' }),
  ).toBeInTheDocument();
});

test('운영자 상세 404를 일반 서버 오류와 구분한다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response({ code: 'NOT_FOUND' }, 404));

  renderAt('/operator/estimate-requests/404');

  expect(
    await screen.findByRole('heading', { name: '견적 요청을 찾을 수 없어요' }),
  ).toBeInTheDocument();
});

test('운영자가 실제 발송 결과를 저장한 뒤 완료 기록을 읽기 전용으로 확인한다', async () => {
  authenticateTestOperator();
  const completedRequest = {
    ...PENDING_DETAIL,
    status: 'ESTIMATE_NOTIFIED',
    manualNotification: {
      messageContent: '가상 사용자에게 실제로 보낸 테스트 문자입니다.',
      transportFeasible: true,
      estimatedAmount: 30000,
      notifiedAt: '2026-08-06T10:05:00+09:00',
    },
  };
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response(undefined, 204))
    .mockResolvedValueOnce(response(completedRequest));
  renderAt('/operator/estimate-requests/12');

  expect(
    await screen.findByRole('heading', { name: '견적 요청 상세' }),
  ).toBeInTheDocument();
  const user = userEvent.setup();
  await user.click(screen.getByRole('radio', { name: '운송 가능' }));
  await user.type(screen.getByLabelText(/^예상 금액/), '30000');
  await user.type(
    screen.getByLabelText('실제로 보낸 문자 내용'),
    '가상 사용자에게 실제로 보낸 테스트 문자입니다.',
  );
  await user.click(screen.getByRole('button', { name: '발송 완료로 저장' }));

  expect(
    await screen.findByRole('heading', { name: '문자 안내 결과' }),
  ).toBeInTheDocument();
  expect(screen.getByText('30,000원')).toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: '발송 완료로 저장' }),
  ).not.toBeInTheDocument();

  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/estimate-requests/12/manual-notification',
  );
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({
      body: JSON.stringify({
        messageContent: '가상 사용자에게 실제로 보낸 테스트 문자입니다.',
        transportFeasible: true,
        estimatedAmount: 30000,
      }),
      method: 'POST',
    }),
  );
  expectOperatorSecretHeader(0);
  expectOperatorSecretHeader(1);
  expectOperatorSecretHeader(2);
});

test('운송 불가 완료는 예상 금액 없이 ESTIMATE_NOTIFIED 결과로 저장한다', async () => {
  authenticateTestOperator();
  const completedRequest = {
    ...PENDING_DETAIL,
    status: 'ESTIMATE_NOTIFIED',
    manualNotification: {
      messageContent: '현재 조건에서는 운송이 어려운 가상 요청입니다.',
      transportFeasible: false,
      estimatedAmount: null,
      notifiedAt: '2026-08-06T10:05:00+09:00',
    },
  };
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response(undefined, 204))
    .mockResolvedValueOnce(response(completedRequest));
  renderAt('/operator/estimate-requests/12');

  await screen.findByRole('heading', { name: '견적 요청 상세' });
  const user = userEvent.setup();
  await user.click(screen.getByRole('radio', { name: '운송 불가' }));
  await user.type(
    screen.getByLabelText('실제로 보낸 문자 내용'),
    '현재 조건에서는 운송이 어려운 가상 요청입니다.',
  );
  await user.click(screen.getByRole('button', { name: '발송 완료로 저장' }));

  expect(await screen.findByText('없음')).toBeInTheDocument();
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({
      body: JSON.stringify({
        messageContent: '현재 조건에서는 운송이 어려운 가상 요청입니다.',
        transportFeasible: false,
        estimatedAmount: null,
      }),
    }),
  );
});

test.each([{}, { unknownField: '알 수 없는 테스트 오류' }])(
  '빈 값 또는 알 수 없는 400 fieldErrors는 공통 저장 오류로 표시한다',
  async (fieldErrors) => {
    authenticateTestOperator();
    fetchMock
      .mockResolvedValueOnce(response(PENDING_DETAIL))
      .mockResolvedValueOnce(response({ code: 'INVALID_INPUT', fieldErrors }, 400));
    renderAt('/operator/estimate-requests/12');

    await screen.findByRole('heading', { name: '견적 요청 상세' });
    const user = userEvent.setup();
    await user.click(screen.getByRole('radio', { name: '운송 불가' }));
    await user.type(
      screen.getByLabelText('실제로 보낸 문자 내용'),
      '가상 사용자에게 보낸 테스트 문자입니다.',
    );
    await user.click(screen.getByRole('button', { name: '발송 완료로 저장' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '안내 완료 기록을 저장하지 못했습니다. 다시 시도해 주세요.',
    );
  },
);

test('로그아웃하면 서버 요청 없이 저장된 비밀번호를 삭제한다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response([]));
  renderAt('/operator/estimate-requests');

  await screen.findByRole('heading', { name: '접수된 견적 요청이 없어요' });
  await userEvent.click(screen.getByRole('button', { name: '로그아웃' }));

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(getOperatorSecret()).toBeNull();
});
