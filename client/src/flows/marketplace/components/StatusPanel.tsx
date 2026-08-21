import MarketplaceIcon from './MarketplaceIcon';
import styles from './StatusPanel.module.css';

interface StatusPanelProps {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  variant?: 'default' | 'error';
}

export default function StatusPanel({
  title,
  description,
  actionLabel,
  onAction,
  variant = 'default',
}: StatusPanelProps) {
  return (
    <section
      aria-live="polite"
      className={`${styles.panel} ${variant === 'error' ? styles.error : ''}`}
    >
      <div aria-hidden="true" className={styles.symbol}>
        {variant === 'error' ? '!' : '· · ·'}
      </div>
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.description}>{description}</p>
      {actionLabel && onAction ? (
        <button className={styles.action} onClick={onAction} type="button">
          <MarketplaceIcon className={styles.actionIcon} name="refresh" />
          {actionLabel}
        </button>
      ) : null}
    </section>
  );
}
