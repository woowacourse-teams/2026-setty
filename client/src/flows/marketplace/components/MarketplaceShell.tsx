import type { PropsWithChildren } from 'react';
import MarketplaceHeader from './MarketplaceHeader';
import styles from './MarketplaceShell.module.css';

interface MarketplaceShellProps extends PropsWithChildren {
  showHeader?: boolean;
}

export default function MarketplaceShell({
  children,
  showHeader = true,
}: MarketplaceShellProps) {
  return (
    <div className={styles.canvas}>
      <div className={styles.shell}>
        {showHeader ? <MarketplaceHeader /> : null}
        {children}
      </div>
    </div>
  );
}
