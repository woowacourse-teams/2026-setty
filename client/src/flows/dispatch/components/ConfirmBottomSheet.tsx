import { useCallback, useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import PrimaryButton from './PrimaryButton';
import { ErrorMessage } from './StatusMessage';
import TextButton from './TextButton';
import styles from './ConfirmBottomSheet.module.css';

interface ConfirmBottomSheetProps {
  /** 닫혀 있으면 아무것도 그리지 않는다. */
  open: boolean;
  title: string;
  /** 시안처럼 줄바꿈과 강조가 섞이므로 node를 그대로 받는다. */
  description: ReactNode;
  confirmLabel: string;
  cancelLabel: string;
  /** 전송 중에는 두 action을 잠가 같은 요청을 두 번 보내지 않는다. */
  submitting?: boolean;
  /** 요청 실패를 시트 안에서 알리고 그 자리에서 다시 시도하게 한다. */
  errorMessage?: string;
  onConfirm: () => void;
  /** 취소 action, 딤 영역, Esc가 모두 이 handler로 닫는다. */
  onClose: () => void;
}

/** focus를 시트 안에 가두기 위한 대상. 현재 시트에는 button만 있다. */
const FOCUSABLE_SELECTOR = 'button:not([disabled])';

/**
 * 되돌릴 수 없는 action 직전에 한 번 더 확인받는 바텀 시트다.
 * `<dialog>` 대신 직접 구현한 이유는 시안의 모바일 프레임 폭에 맞춰
 * 시트를 정렬해야 하고, 열림 상태를 React state 한곳에서만 다루기 위해서다.
 */
export default function ConfirmBottomSheet({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel,
  submitting = false,
  errorMessage = '',
  onConfirm,
  onClose,
}: ConfirmBottomSheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null);
  /** 시트를 닫은 뒤 원래 누르던 버튼으로 focus를 돌려준다. */
  const openerRef = useRef<HTMLElement | null>(null);

  /** 전송 중에는 사용자가 닫아도 이미 나간 요청을 되돌릴 수 없으므로 막는다. */
  const requestClose = useCallback(() => {
    if (submitting) {
      return;
    }
    onClose();
  }, [onClose, submitting]);

  useEffect(() => {
    if (!open) {
      return;
    }

    openerRef.current = document.activeElement as HTMLElement | null;

    // 시트 뒤 화면이 같이 스크롤되면 어떤 화면을 조작하는지 알기 어렵다.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.body.style.overflow = previousOverflow;
      openerRef.current?.focus();
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }

    // 열리면 주요 action에 focus를 둬 키보드로도 바로 확인할 수 있게 한다.
    const focusables = sheetRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
    focusables?.[0]?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        requestClose();
        return;
      }

      if (event.key !== 'Tab') {
        return;
      }

      const focusables = Array.from(
        sheetRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? [],
      );
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (!first || !last) {
        return;
      }

      const active = document.activeElement;

      // 시트 밖으로 나가려는 이동만 반대쪽 끝으로 되돌린다.
      if (event.shiftKey && (active === first || !sheetRef.current?.contains(active))) {
        event.preventDefault();
        last.focus();
        return;
      }
      if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, requestClose]);

  if (!open) {
    return null;
  }

  return createPortal(
    <div
      className={styles.overlay}
      // 딤을 눌러 닫는 동작이고, 시트 안의 클릭은 여기까지 올라오지 않는다.
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          requestClose();
        }
      }}
    >
      <div
        ref={sheetRef}
        className={styles.sheet}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-bottom-sheet-title"
      >
        <h2 id="confirm-bottom-sheet-title" className={styles.title}>
          {title}
        </h2>
        <p className={styles.description}>{description}</p>

        {errorMessage ? <ErrorMessage message={errorMessage} /> : null}

        <div className={styles.actions}>
          <PrimaryButton onClick={onConfirm} disabled={submitting}>
            {confirmLabel}
          </PrimaryButton>
          <TextButton onClick={requestClose} disabled={submitting}>
            {cancelLabel}
          </TextButton>
        </div>
      </div>
    </div>,
    document.body,
  );
}
