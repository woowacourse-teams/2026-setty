import type { ButtonHTMLAttributes } from 'react';
import styles from './PrimaryButton.module.css';

type PrimaryButtonProps = ButtonHTMLAttributes<HTMLButtonElement>;

/** 시안의 56px 초록 주요 action 버튼 */
export default function PrimaryButton({ type = 'button', ...props }: PrimaryButtonProps) {
  return <button className={styles.button} type={type} {...props} />;
}
