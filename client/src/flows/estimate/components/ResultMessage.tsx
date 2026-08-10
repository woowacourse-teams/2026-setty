import styles from './ResultMessage.module.css';

interface ResultMessageProps {
  /** 시안의 큰 이모지 */
  emoji: string;
  title: string;
  /** 줄바꿈은 `\n`으로 넣는다. */
  description: string;
}

/** 이모지 + 제목 + 설명으로 구성된 결과 화면 본문 */
export default function ResultMessage({ emoji, title, description }: ResultMessageProps) {
  return (
    <div className={styles.result}>
      <span className={styles.emoji} role="img" aria-hidden="true">
        {emoji}
      </span>
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.description}>{description}</p>
    </div>
  );
}
