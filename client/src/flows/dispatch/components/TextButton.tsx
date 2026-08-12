import type { ButtonHTMLAttributes } from 'react';
import styles from './TextButton.module.css';

type TextButtonProps = ButtonHTMLAttributes<HTMLButtonElement>;

/** 시안의 주요 버튼 아래 보조 action */
export default function TextButton({ type = 'button', ...props }: TextButtonProps) {
  return <button className={styles.button} type={type} {...props} />;
}
