import { afterEach, beforeEach, expect, jest, test } from '@jest/globals';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import App from '@/app/App';
import {
  getListingDetail,
  sendListingMessage,
} from '@/flows/marketplace/api/marketplaceApi';

jest.mock('@/flows/marketplace/api/marketplaceApi', () => ({
  ...jest.requireActual<typeof import('@/flows/marketplace/api/marketplaceApi')>(
    '@/flows/marketplace/api/marketplaceApi',
  ),
  getListingDetail: jest.fn(),
  sendListingMessage: jest.fn(),
}));

const getListingDetailMock = jest.mocked(getListingDetail);
const sendListingMessageMock = jest.mocked(sendListingMessage);

const DETAIL = {
  id: 11,
  title: '테스트 원목 책상',
  description:
    '원본 글 https://www.daangn.com/articles/12345\n잘못된 주소 javascript:alert(1)',
  price: 60000,
  pickupTimeText: '평일 오후 7시 이후',
  canHelpMove: true,
  images: [
    {
      id: 1,
      url: 'https://example.test/fake-desk.jpg',
      displayOrder: 0,
    },
  ],
  createdAt: '2026-08-21T14:00:00+09:00',
  updatedAt: '2026-08-21T14:00:00+09:00',
};

function CurrentPath() {
  return <output data-testid="route-path">{useLocation().pathname}</output>;
}

beforeEach(() => {
  getListingDetailMock.mockReset();
  sendListingMessageMock.mockReset();
  getListingDetailMock.mockResolvedValue(DETAIL);
  sendListingMessageMock.mockResolvedValue({
    messageId: 21,
    createdAt: '2026-08-21T14:10:00+09:00',
  });
});

afterEach(() => {
  jest.useRealTimers();
  jest.restoreAllMocks();
});

test('설명의 http/https 주소만 새 창 링크로 만든다', async () => {
  render(
    <MemoryRouter initialEntries={['/listings/11']}>
      <App />
    </MemoryRouter>,
  );

  const link = await screen.findByRole('link', {
    name: 'https://www.daangn.com/articles/12345',
  });
  expect(link).toHaveAttribute('href', 'https://www.daangn.com/articles/12345');
  expect(link).toHaveAttribute('target', '_blank');
  expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  expect(screen.queryByRole('link', { name: /javascript:/ })).not.toBeInTheDocument();
  expect(screen.getByText(/javascript:alert/)).toBeInTheDocument();
});

test('로그인 없이 작성한 익명 쪽지를 해당 매물로 전송한다', async () => {
  jest.useFakeTimers();
  const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
  render(
    <MemoryRouter
      initialEntries={['/', '/listings/11', '/listings/11/message']}
      initialIndex={2}
    >
      <CurrentPath />
      <App />
    </MemoryRouter>,
  );

  expect(await screen.findByText(/테스트 원목 책상 · 익명 문의/)).toBeInTheDocument();
  await user.type(screen.getByLabelText('쪽지 내용'), '주말 오전에 가져갈 수 있어요.');
  await user.click(screen.getByRole('button', { name: '보내기' }));

  await waitFor(() =>
    expect(sendListingMessageMock).toHaveBeenCalledWith(11, {
      content: '주말 오전에 가져갈 수 있어요.',
    }),
  );
  expect(await screen.findByText('판매자에게 쪽지를 남겼어요.')).toHaveAttribute(
    'role',
    'status',
  );

  await user.click(screen.getByRole('button', { name: '취소' }));
  expect(screen.getByTestId('route-path')).toHaveTextContent('/listings/11');

  act(() => jest.advanceTimersByTime(1_000));
  expect(screen.getByTestId('route-path')).toHaveTextContent('/listings/11');
});
