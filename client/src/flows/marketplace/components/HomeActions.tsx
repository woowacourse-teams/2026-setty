import MarketplaceIcon from './MarketplaceIcon';
import styles from './HomeActions.module.css';

interface HomeActionsProps {
  canUndo: boolean;
  disabled?: boolean;
  onDetail: () => void;
  onMessage: () => void;
  onSkip: () => void;
  onUndo: () => void;
}

interface ActionButtonProps {
  className?: string;
  disabled?: boolean;
  icon: 'close' | 'message' | 'right' | 'undo';
  label: string;
  onClick: () => void;
}

function ActionButton({ className, disabled, icon, label, onClick }: ActionButtonProps) {
  return (
    <button
      aria-label={label}
      className={`${styles.action} ${className}`}
      disabled={disabled}
      onClick={onClick}
      title={label}
      type="button"
    >
      <MarketplaceIcon className={styles.icon} name={icon} />
    </button>
  );
}

export default function HomeActions({
  canUndo,
  disabled = false,
  onDetail,
  onMessage,
  onSkip,
  onUndo,
}: HomeActionsProps) {
  return (
    <div aria-label="매물 카드 동작" className={styles.actions} role="group">
      <ActionButton
        className={styles.undo}
        disabled={!canUndo}
        icon="undo"
        label="이전 카드로 되돌리기"
        onClick={onUndo}
      />
      <ActionButton
        className={styles.skip}
        disabled={disabled}
        icon="close"
        label="다음 매물 보기"
        onClick={onSkip}
      />
      <ActionButton
        className={styles.detail}
        disabled={disabled}
        icon="right"
        label="이 매물 상세 보기"
        onClick={onDetail}
      />
      <ActionButton
        className={styles.message}
        disabled={disabled}
        icon="message"
        label="이 매물에 쪽지 보내기"
        onClick={onMessage}
      />
    </div>
  );
}
