import { beforeEach, expect, jest, test } from '@jest/globals';
import '@testing-library/jest-dom/jest-globals';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { buildSellerGuideMessage } from '@/flows/dispatch/model/sellerGuideMessage';
import LinkCreatedScreen from '@/flows/dispatch/screens/LinkCreatedScreen';

const clipboardWriteTextMock = jest.fn<(value: string) => Promise<void>>();
const shareMock = jest.fn<(data: ShareData) => Promise<void>>();
const onNext = jest.fn();

const FAKE_BUYER_TOKEN = 'FAKE_BUYER_TOKEN';
const FAKE_SELLER_INPUT_URL = 'https://example.test/seller-input/FAKE_SELLER_INPUT_TOKEN';
const EXPECTED_MESSAGE = buildSellerGuideMessage(FAKE_SELLER_INPUT_URL);

function renderScreen() {
  render(
    <LinkCreatedScreen
      buyerToken={FAKE_BUYER_TOKEN}
      initialSellerInputUrl={FAKE_SELLER_INPUT_URL}
      onNext={onNext}
    />,
  );
}

beforeEach(() => {
  clipboardWriteTextMock.mockReset();
  clipboardWriteTextMock.mockResolvedValue(undefined);
  shareMock.mockReset();
  shareMock.mockResolvedValue(undefined);
  onNext.mockReset();
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: clipboardWriteTextMock },
  });
  Object.defineProperty(navigator, 'share', {
    configurable: true,
    value: shareMock,
  });
});

test('안내문에 서비스 설명과 판매자 입력 링크를 함께 담는다', () => {
  expect(EXPECTED_MESSAGE).toContain('세티(SETTY)');
  expect(EXPECTED_MESSAGE).toContain(FAKE_SELLER_INPUT_URL);
});

test('링크 복사가 안내문과 링크를 함께 복사한다', async () => {
  renderScreen();

  await userEvent.click(screen.getByRole('button', { name: '안내문과 링크 복사' }));

  expect(clipboardWriteTextMock).toHaveBeenCalledWith(EXPECTED_MESSAGE);
  expect(await screen.findByRole('status')).toHaveTextContent('안내문과 링크를 복사했어요');
  expect(onNext).toHaveBeenCalled();
});

test('링크 공유가 안내문과 링크를 함께 공유한다', async () => {
  renderScreen();

  await userEvent.click(screen.getByRole('button', { name: '안내문과 링크 공유하기' }));

  expect(shareMock).toHaveBeenCalledWith({ text: EXPECTED_MESSAGE });
  expect(onNext).toHaveBeenCalled();
});

test('복사에 실패하면 성공으로 표시하지 않고 안내문 전문을 보여준다', async () => {
  clipboardWriteTextMock.mockRejectedValue(new Error('copy failed'));
  renderScreen();

  await userEvent.click(screen.getByRole('button', { name: '안내문과 링크 복사' }));

  expect(onNext).not.toHaveBeenCalled();
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
  expect(screen.getByTestId('seller-guide-message')).toHaveTextContent('세티(SETTY)');
});
