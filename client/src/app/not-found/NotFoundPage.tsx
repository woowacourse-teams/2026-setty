import styles from './NotFoundPage.module.css';

export default function NotFoundPage() {
  return (
    <main className={styles.notFound}>
      <section>
        <p className={styles.notFoundCode}>404</p>
        <h1>페이지를 찾을 수 없어요</h1>
        <p className={styles.notFoundDescription}>요청한 주소를 다시 확인해 주세요.</p>
      </section>
    </main>
  );
}
