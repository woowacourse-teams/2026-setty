import { beforeEach, expect, jest, test } from '@jest/globals';
import '@testing-library/jest-dom/jest-globals';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useRoutes } from 'react-router-dom';
import { estimateRoutes } from './routes';

const fetchMock = jest.fn<typeof fetch>();

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

function EstimateTestRoutes() {
  return useRoutes(estimateRoutes);
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <EstimateTestRoutes />
    </MemoryRouter>,
  );
}

async function fillValidEstimateForm() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('상품명'), '테스트 의자');
  await user.type(screen.getByLabelText('거래 지역'), '테스트구 테스트동');
  await user.type(screen.getByLabelText('이름'), '테스트사용자');
  await user.type(screen.getByLabelText('연락처'), '010-0000-0000');
  return user;
}

beforeEach(() => {
  fetchMock.mockReset();
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    value: fetchMock,
    writable: true,
  });
});

test('견적 접수 완료 직접 URL은 루트 경로와 독립적으로 동작한다', () => {
  renderAt('/estimate/submitted');

  expect(screen.getByRole('heading', { name: '요청이 접수됐어요' })).toBeInTheDocument();
});

test('예상 견적 개인정보 처리 안내 직접 URL이 동작한다', () => {
  renderAt('/estimate/privacy');

  expect(
    screen.getByRole('heading', { name: '예상 견적 개인정보 처리 안내' }),
  ).toBeInTheDocument();
  expect(screen.getByText('견적 안내 완료 시점부터 30일')).toBeInTheDocument();
  expect(screen.getAllByRole('link', { name: 'setty@example.com' })).toHaveLength(2);
});

test('필수 입력을 확인하고 사용자에게 각 필드 오류를 표시한다', async () => {
  const user = userEvent.setup();
  renderAt('/estimate');

  await user.click(screen.getByRole('button', { name: '예상 견적 요청하기' }));

  expect(screen.getByRole('textbox', { name: '상품명' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: '거래 지역' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: '이름' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: '연락처' })).toBeInTheDocument();
  expect(screen.getByText('이름을 입력해 주세요.')).toBeInTheDocument();
  expect(
    screen.getByText('010으로 시작하는 휴대전화 번호 11자리를 입력해 주세요.'),
  ).toBeInTheDocument();
  expect(screen.getByText('거래 지역을 입력해 주세요.')).toBeInTheDocument();
  expect(screen.getByText('상품명을 입력해 주세요.')).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
});

test('견적 요청을 정규화해 제출하고 접수 완료 화면으로 이동한다', async () => {
  fetchMock.mockResolvedValueOnce(
    response({
      estimateRequestId: 12,
      status: 'PENDING_REVIEW',
      createdAt: '2026-08-06T10:00:00+09:00',
    }),
  );
  renderAt('/estimate');
  const user = await fillValidEstimateForm();

  await user.click(screen.getByRole('button', { name: '예상 견적 요청하기' }));

  expect(
    await screen.findByRole('heading', { name: '요청이 접수됐어요' }),
  ).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/estimate-requests',
    expect.objectContaining({
      body: JSON.stringify({
        name: '테스트사용자',
        phoneNumber: '01000000000',
        tradeArea: '테스트구 테스트동',
        itemType: '테스트 의자',
        highValueItem: false,
      }),
      credentials: 'same-origin',
      method: 'POST',
    }),
  );
});

test('거래 금액 체크를 켜면 50만 원 초과 여부를 true로 보낸다', async () => {
  fetchMock.mockResolvedValueOnce(
    response({
      estimateRequestId: 13,
      status: 'PENDING_REVIEW',
      createdAt: '2026-08-06T10:00:00+09:00',
    }),
  );
  renderAt('/estimate');
  const user = await fillValidEstimateForm();

  await user.click(screen.getByRole('button', { name: '50만원 이상 거래예요' }));
  await user.click(screen.getByRole('button', { name: '예상 견적 요청하기' }));

  expect(
    await screen.findByRole('heading', { name: '요청이 접수됐어요' }),
  ).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/estimate-requests',
    expect.objectContaining({
      body: JSON.stringify({
        name: '테스트사용자',
        phoneNumber: '01000000000',
        tradeArea: '테스트구 테스트동',
        itemType: '테스트 의자',
        highValueItem: true,
      }),
      credentials: 'same-origin',
      method: 'POST',
    }),
  );
});

test('서버 필드 오류를 기존 입력 오류 위치에 표시한다', async () => {
  fetchMock.mockResolvedValueOnce(
    response(
      {
        code: 'INVALID_INPUT',
        message: '입력값을 확인해 주세요.',
        fieldErrors: { phoneNumber: '서버에서 확인한 연락처 오류입니다.' },
      },
      400,
    ),
  );
  renderAt('/estimate');
  const user = await fillValidEstimateForm();

  await user.click(screen.getByRole('button', { name: '예상 견적 요청하기' }));

  expect(
    await screen.findByText('서버에서 확인한 연락처 오류입니다.'),
  ).toBeInTheDocument();
});

test('사용자 요청 중에는 중복 제출을 막고 공통 서버 오류를 구분한다', async () => {
  fetchMock.mockReturnValueOnce(new Promise<Response>(() => undefined));
  renderAt('/estimate');
  const user = await fillValidEstimateForm();

  await user.click(screen.getByRole('button', { name: '예상 견적 요청하기' }));

  expect(screen.getByRole('button', { name: '접수하고 있어요…' })).toBeDisabled();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

test('사용자 요청의 공통 서버 오류를 폼 전체 오류로 표시한다', async () => {
  fetchMock.mockResolvedValueOnce(response({ code: 'INTERNAL_SERVER_ERROR' }, 500));
  renderAt('/estimate');
  const user = await fillValidEstimateForm();

  await user.click(screen.getByRole('button', { name: '예상 견적 요청하기' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '요청을 접수하지 못했어요. 잠시 후 다시 시도해 주세요.',
  );
  expect(screen.getByRole('button', { name: '예상 견적 요청하기' })).toBeEnabled();
});
