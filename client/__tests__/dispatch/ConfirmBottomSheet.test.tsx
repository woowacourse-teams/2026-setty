import { useState } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ConfirmBottomSheet from '@/flows/dispatch/components/ConfirmBottomSheet';

/**
 * 되돌릴 수 없는 action 앞에 서는 확인 시트라 닫는 경로와 잠금을 함께 확인한다.
 */

const TITLE = '이대로 진행할까요?';
const CONFIRM_LABEL = '네, 진행할게요';
const CANCEL_LABEL = '다시 볼게요';

const onConfirm = jest.fn();
const onClose = jest.fn();

const renderSheet = (props: { submitting?: boolean } = {}) =>
  render(
    <ConfirmBottomSheet
      open
      title={TITLE}
      description="진행하면 취소할 수 없어요."
      confirmLabel={CONFIRM_LABEL}
      cancelLabel={CANCEL_LABEL}
      onConfirm={onConfirm}
      onClose={onClose}
      {...props}
    />,
  );

const dimArea = () => {
  const overlay = screen.getByRole('dialog').parentElement;
  if (!overlay) {
    throw new Error('딤 영역을 찾을 수 없습니다.');
  }

  return overlay;
};

beforeEach(() => {
  onConfirm.mockReset();
  onClose.mockReset();
});

it('닫혀 있으면 아무것도 그리지 않는다', () => {
  render(
    <ConfirmBottomSheet
      open={false}
      title={TITLE}
      description="진행하면 취소할 수 없어요."
      confirmLabel={CONFIRM_LABEL}
      cancelLabel={CANCEL_LABEL}
      onConfirm={onConfirm}
      onClose={onClose}
    />,
  );

  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('딤 영역을 눌러 닫고, 시트 안 클릭은 닫지 않는다', async () => {
  const user = userEvent.setup();
  renderSheet();

  await user.click(screen.getByRole('dialog'));
  expect(onClose).not.toHaveBeenCalled();

  await user.click(dimArea());
  expect(onClose).toHaveBeenCalledTimes(1);
  expect(onConfirm).not.toHaveBeenCalled();
});

it('열려 있는 동안 뒤 화면 스크롤을 막고 닫으면 되돌린다', () => {
  const { unmount } = renderSheet();

  expect(document.body.style.overflow).toBe('hidden');

  unmount();
  expect(document.body.style.overflow).toBe('');
});

it('focus를 시트 안에 가두고 닫은 뒤 원래 버튼으로 돌려준다', async () => {
  const user = userEvent.setup();

  function ScreenWithSheet() {
    const [open, setOpen] = useState(false);

    return (
      <>
        <button type="button" onClick={() => setOpen(true)}>
          진행하기
        </button>
        <ConfirmBottomSheet
          open={open}
          title={TITLE}
          description="진행하면 취소할 수 없어요."
          confirmLabel={CONFIRM_LABEL}
          cancelLabel={CANCEL_LABEL}
          onConfirm={onConfirm}
          onClose={() => setOpen(false)}
        />
      </>
    );
  }

  render(<ScreenWithSheet />);
  const opener = screen.getByRole('button', { name: '진행하기' });

  await user.click(opener);
  expect(screen.getByRole('button', { name: CONFIRM_LABEL })).toHaveFocus();

  // 마지막 action에서 Tab을 눌러도 시트 밖으로 나가지 않는다.
  await user.tab();
  expect(screen.getByRole('button', { name: CANCEL_LABEL })).toHaveFocus();
  await user.tab();
  expect(screen.getByRole('button', { name: CONFIRM_LABEL })).toHaveFocus();
  await user.tab({ shift: true });
  expect(screen.getByRole('button', { name: CANCEL_LABEL })).toHaveFocus();

  await user.keyboard('{Escape}');
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(opener).toHaveFocus();
});

it('전송 중에는 두 action과 닫기를 모두 잠근다', async () => {
  const user = userEvent.setup();
  renderSheet({ submitting: true });

  expect(screen.getByRole('button', { name: CONFIRM_LABEL })).toBeDisabled();
  expect(screen.getByRole('button', { name: CANCEL_LABEL })).toBeDisabled();

  await user.click(dimArea());
  await user.keyboard('{Escape}');

  expect(onClose).not.toHaveBeenCalled();
  expect(onConfirm).not.toHaveBeenCalled();
});
