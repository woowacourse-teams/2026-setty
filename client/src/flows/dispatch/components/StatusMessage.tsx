import styles from './StatusMessage.module.css';

/** server 실패를 성공처럼 보이지 않게 그대로 보여준다. */
export function ErrorMessage({ message }: { message: string }) {
  return (
    <p className={styles.error} role="alert">
      {message}
    </p>
  );
}

export function LoadingMessage({ message = '불러오는 중이에요' }: { message?: string }) {
  return (
    <p className={styles.loading} role="status">
      {message}
    </p>
  );
}
