import { beforeEach, expect, jest, test } from '@jest/globals';
import '@testing-library/jest-dom/jest-globals';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate, useRoutes } from 'react-router-dom';
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

function OperatorRouteSwitchTest() {
  const navigate = useNavigate();

  return (
    <>
      <button type="button" onClick={() => navigate('/operator/estimate-requests/13')}>
        테스트 요청 전환
      </button>
      <OperatorTestRoutes />
    </>
  );
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <OperatorTestRoutes />
    </MemoryRouter>,
  );
}

function renderAtWithRouteSwitch(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <OperatorRouteSwitchTest />
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

test('상세 요청 ID가 바뀌면 이전 요청의 메시지 폼을 새 요청 로딩 중에 숨긴다', async () => {
  authenticateTestOperator();
  const previousMessage = '이전 가상 요청에 저장된 문자입니다.';
  fetchMock
    .mockResolvedValueOnce(
      response({
        ...PENDING_DETAIL,
        status: 'ESTIMATE_NOTIFIED',
        manualNotification: {
          messageContent: previousMessage,
          transportFeasible: true,
        },
      }),
    )
    .mockImplementationOnce(() => new Promise<Response>(() => undefined));
  renderAtWithRouteSwitch('/operator/estimate-requests/12');

  expect(await screen.findByLabelText('문자 안내 및 상황 기록')).toHaveValue(
    previousMessage,
  );
  await userEvent.click(screen.getByRole('button', { name: '테스트 요청 전환' }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  expect(screen.getByRole('status')).toHaveTextContent('견적 요청을 불러오고 있어요');
  expect(screen.queryByLabelText('문자 안내 및 상황 기록')).not.toBeInTheDocument();
});

test('상세 요청 ID 전환 후 새 요청이 404이면 찾을 수 없음 상태를 표시한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response({ code: 'NOT_FOUND' }, 404));
  renderAtWithRouteSwitch('/operator/estimate-requests/12');

  await screen.findByRole('heading', { name: '견적 요청 상세' });
  await userEvent.click(screen.getByRole('button', { name: '테스트 요청 전환' }));

  expect(
    await screen.findByRole('heading', { name: '견적 요청을 찾을 수 없어요' }),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText('문자 안내 및 상황 기록')).not.toBeInTheDocument();
});

test('운영자가 문자 안내를 최초 저장한 뒤 같은 폼에서 저장값을 확인한다', async () => {
  authenticateTestOperator();
  const savedMessage = '예상 운송비는 30,000원인 가상 안내입니다.';
  const typedMessage = `  ${savedMessage}  `;
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response(undefined, 204));
  renderAt('/operator/estimate-requests/12');

  expect(
    await screen.findByRole('heading', { name: '견적 요청 상세' }),
  ).toBeInTheDocument();
  const user = userEvent.setup();
  await user.click(screen.getByRole('radio', { name: '운송 가능' }));
  await user.type(screen.getByLabelText('문자 안내 및 상황 기록'), typedMessage);
  await user.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(await screen.findByText('메시지를 저장했습니다.')).toBeInTheDocument();
  expect(screen.getByRole('radio', { name: '운송 가능' })).toBeChecked();
  expect(screen.getByLabelText('문자 안내 및 상황 기록')).toHaveValue(savedMessage);
  expect(screen.getByRole('button', { name: '메시지 저장' })).toHaveFocus();
  expect(
    screen.queryByRole('spinbutton', { name: /^예상 금액/ }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText('안내 완료 시각')).not.toBeInTheDocument();

  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  expect(fetchMock.mock.calls[1]?.[0]).toBe(
    'http://localhost:8080/api/operator/estimate-requests/12/manual-notification',
  );
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({
      body: JSON.stringify({
        messageContent: savedMessage,
        transportFeasible: true,
      }),
      method: 'PUT',
    }),
  );
  expectOperatorSecretHeader(0);
  expectOperatorSecretHeader(1);
});

test('안내 완료 기록의 기존 본문과 운송 판단을 수정해 같은 PUT으로 저장한다', async () => {
  authenticateTestOperator();
  const initialMessage = '현재 조건에서는 운송이 어려운 가상 요청입니다.';
  const updatedMessage = `${initialMessage}\n추가 확인 후 운송 가능한 것으로 변경했습니다.`;
  const completedRequest = {
    ...PENDING_DETAIL,
    status: 'ESTIMATE_NOTIFIED',
    manualNotification: {
      messageContent: initialMessage,
      transportFeasible: false,
    },
  };
  fetchMock
    .mockResolvedValueOnce(response(completedRequest))
    .mockResolvedValueOnce(response(undefined, 204));
  renderAt('/operator/estimate-requests/12');

  const messageInput = await screen.findByLabelText('문자 안내 및 상황 기록');
  expect(messageInput).toHaveValue(initialMessage);
  expect(screen.getByRole('radio', { name: '운송 불가' })).toBeChecked();

  const user = userEvent.setup();
  await user.type(messageInput, '{enter}추가 확인 후 운송 가능한 것으로 변경했습니다.  ');
  await user.click(screen.getByRole('radio', { name: '운송 가능' }));
  await user.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(await screen.findByText('메시지를 저장했습니다.')).toBeInTheDocument();
  expect(screen.getByLabelText('문자 안내 및 상황 기록')).toHaveValue(updatedMessage);
  expect(screen.getByRole('radio', { name: '운송 가능' })).toBeChecked();
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  expect(fetchMock.mock.calls[1]?.[1]).toEqual(
    expect.objectContaining({
      body: JSON.stringify({
        messageContent: updatedMessage,
        transportFeasible: true,
      }),
      method: 'PUT',
    }),
  );
});

test('문자 내용과 운송 가능 여부가 비어 있으면 PUT 요청을 보내지 않는다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response(PENDING_DETAIL));
  renderAt('/operator/estimate-requests/12');

  await screen.findByRole('heading', { name: '견적 요청 상세' });
  await userEvent.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(screen.getByText('실제로 보낸 문자 내용을 입력해 주세요.')).toBeInTheDocument();
  expect(screen.getByText('운송 가능 여부를 선택해 주세요.')).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

test('알려진 400 fieldErrors를 문자와 운송 가능 여부 입력에 표시한다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(response(PENDING_DETAIL)).mockResolvedValueOnce(
    response(
      {
        code: 'INVALID_INPUT',
        fieldErrors: {
          messageContent: '테스트 문자 오류입니다.',
          transportFeasible: '테스트 운송 판단 오류입니다.',
        },
      },
      400,
    ),
  );
  renderAt('/operator/estimate-requests/12');

  await screen.findByRole('heading', { name: '견적 요청 상세' });
  const user = userEvent.setup();
  await user.click(screen.getByRole('radio', { name: '운송 가능' }));
  await user.type(
    screen.getByLabelText('문자 안내 및 상황 기록'),
    '가상 사용자에게 보낸 테스트 문자입니다.',
  );
  await user.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(await screen.findByText('테스트 문자 오류입니다.')).toBeInTheDocument();
  expect(screen.getByText('테스트 운송 판단 오류입니다.')).toBeInTheDocument();
});

test('저장 중 409가 발생하면 작성 중인 문자 기록을 유지하고 충돌을 알린다', async () => {
  authenticateTestOperator();
  const draftMessage = '가상 사용자에게 보낸 테스트 문자입니다.';
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response({ code: 'INVALID_ESTIMATE_REQUEST_STATUS' }, 409));
  renderAt('/operator/estimate-requests/12');

  await screen.findByRole('heading', { name: '견적 요청 상세' });
  const user = userEvent.setup();
  await user.click(screen.getByRole('radio', { name: '운송 가능' }));
  await user.type(screen.getByLabelText('문자 안내 및 상황 기록'), draftMessage);
  await user.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '요청 상태가 변경되어 메시지를 저장하지 못했습니다.',
  );
  expect(screen.getByLabelText('문자 안내 및 상황 기록')).toHaveValue(draftMessage);
  expect(screen.getByRole('radio', { name: '운송 가능' })).toBeChecked();
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

test('안내 완료 상태에 문자 기록이 없으면 수정 폼 대신 계약 경고를 표시한다', async () => {
  authenticateTestOperator();
  fetchMock.mockResolvedValueOnce(
    response({
      ...PENDING_DETAIL,
      status: 'ESTIMATE_NOTIFIED',
      manualNotification: null,
    }),
  );
  renderAt('/operator/estimate-requests/12');

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '안내 완료 상태이지만 저장된 문자 기록을 받지 못했습니다.',
  );
  expect(screen.queryByRole('button', { name: '메시지 저장' })).not.toBeInTheDocument();
});

test('문자 저장 요청이 401이면 비밀번호를 삭제하고 로그인 화면으로 이동한다', async () => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401));
  renderAt('/operator/estimate-requests/12');

  await screen.findByRole('heading', { name: '견적 요청 상세' });
  const user = userEvent.setup();
  await user.click(screen.getByRole('radio', { name: '운송 불가' }));
  await user.type(
    screen.getByLabelText('문자 안내 및 상황 기록'),
    '가상 사용자에게 보낸 테스트 문자입니다.',
  );
  await user.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(
    await screen.findByRole('heading', { name: '운영자 로그인' }),
  ).toBeInTheDocument();
  expect(getOperatorSecret()).toBeNull();
});

test.each([
  {
    caseName: '빈 400 fieldErrors',
    body: { code: 'INVALID_INPUT', fieldErrors: {} },
    status: 400,
  },
  {
    caseName: '알 수 없는 400 fieldErrors',
    body: {
      code: 'INVALID_INPUT',
      fieldErrors: { unknownField: '알 수 없는 테스트 오류' },
    },
    status: 400,
  },
  {
    caseName: '500 응답',
    body: { code: 'INTERNAL_SERVER_ERROR' },
    status: 500,
  },
])('$caseName는 공통 저장 오류로 표시한다', async ({ body, status }) => {
  authenticateTestOperator();
  fetchMock
    .mockResolvedValueOnce(response(PENDING_DETAIL))
    .mockResolvedValueOnce(response(body, status));
  renderAt('/operator/estimate-requests/12');

  await screen.findByRole('heading', { name: '견적 요청 상세' });
  const user = userEvent.setup();
  await user.click(screen.getByRole('radio', { name: '운송 불가' }));
  await user.type(
    screen.getByLabelText('문자 안내 및 상황 기록'),
    '가상 사용자에게 보낸 테스트 문자입니다.',
  );
  await user.click(screen.getByRole('button', { name: '메시지 저장' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '메시지를 저장하지 못했습니다. 다시 시도해 주세요.',
  );
});

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
