import type { ButtonHTMLAttributes } from 'react';
import styles from './SecondaryButton.module.css';

type SecondaryButtonProps = ButtonHTMLAttributes<HTMLButtonElement>;

/** 시안의 주요 버튼 바로 아래에 오는 회색 채움 보조 action */
export default function SecondaryButton({
  type = 'button',
  ...props
}: SecondaryButtonProps) {
  return <button className={styles.button} type={type} {...props} />;
}
