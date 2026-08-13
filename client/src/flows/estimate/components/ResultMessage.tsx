import styles from './ResultMessage.module.css';

interface ResultMessageProps {
  /**
   * 시안의 큰 아이콘 이미지다.
   * 시스템 이모지는 OS·브라우저마다 모양이 달라 아이콘 이미지를 쓴다.
   */
  iconSrc: string;
  title: string;
  /** 줄바꿈은 `\n`으로 넣는다. */
  description: string;
}

/** 아이콘 + 제목 + 설명으로 구성된 결과 화면 본문 */
export default function ResultMessage({ iconSrc, title, description }: ResultMessageProps) {
  return (
    <div className={styles.result}>
      {/* 제목·설명이 같은 내용을 말하므로 아이콘은 장식으로 둔다. */}
      <img className={styles.icon} src={iconSrc} alt="" />
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.description}>{description}</p>
    </div>
  );
}
