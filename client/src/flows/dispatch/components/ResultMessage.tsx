import type { ReactNode } from 'react';
import styles from './ResultMessage.module.css';

interface ResultMessageProps {
  /** 시안의 큰 이모지 */
  emoji: string;
  /** 시안처럼 제목 일부만 강조해야 하는 화면이 있어 node를 허용한다. */
  title: ReactNode;
  /** 줄바꿈은 `\n`으로 넣는다. */
  description: string;
  /** 설명 아래 추가로 보여줄 내용 */
  children?: ReactNode;
}

/** 이모지 + 제목 + 설명으로 구성된 결과 화면 본문 */
export default function ResultMessage({
  emoji,
  title,
  description,
  children,
}: ResultMessageProps) {
  return (
    <div className={styles.result}>
      <span className={styles.emoji} role="img" aria-hidden="true">
        {emoji}
      </span>
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.description}>{description}</p>
      {children}
    </div>
  );
}
