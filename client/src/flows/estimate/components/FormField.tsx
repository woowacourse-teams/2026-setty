import { useId } from 'react';
import type { InputHTMLAttributes } from 'react';
import styles from './FormField.module.css';

interface FormFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  /** 입력 아래 안내 문구 */
  hint?: string;
  /** 입력 오류 문구. 있으면 hint 대신 보여준다. */
  error?: string;
}

/** 시안의 라벨 + 밑줄 입력 필드 */
export default function FormField({ label, hint, error, ...inputProps }: FormFieldProps) {
  const inputId = useId();
  const messageId = `${inputId}-message`;
  const message = error ?? hint;

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={inputId}>
        {label}
      </label>
      <input
        className={styles.input}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        {...inputProps}
      />
      {message ? (
        <p
          className={error ? styles.error : styles.hint}
          id={messageId}
          role={error ? 'alert' : undefined}
        >
          {message}
        </p>
      ) : null}
    </div>
  );
}
