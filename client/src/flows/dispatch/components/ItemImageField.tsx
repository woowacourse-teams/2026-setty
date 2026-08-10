import { useId } from 'react';
import closeIcon from '../assets/close.svg';
import styles from './ItemImageField.module.css';

interface ItemImageFieldProps {
  /** 미리보기 URL. 첨부가 없으면 `null`이다. */
  previewUrl: string | null;
  /** 파일을 고르면 호출한다. 취소하면 호출하지 않는다. */
  onSelect: (file: File) => void;
  onRemove: () => void;
  /** 이미지가 아닌 파일 등 첨부 실패 안내 */
  error?: string;
}

export const ITEM_IMAGE_LABEL = '물품 상태 사진';

/**
 * 시안 `물품 상태 사진` — 선택 항목이다.
 * 첨부 전에는 오른쪽 `업로드` 버튼만, 첨부 후에는 미리보기와 삭제 버튼을 보여준다.
 */
export default function ItemImageField({
  previewUrl,
  onSelect,
  onRemove,
  error,
}: ItemImageFieldProps) {
  const inputId = useId();
  const errorId = `${inputId}-error`;

  return (
    <div className={styles.field}>
      <div className={styles.row}>
        <span className={styles.label}>{ITEM_IMAGE_LABEL}</span>
        {/* 시안의 초록 `업로드` 버튼이 곧 파일 선택 label이다. */}
        {previewUrl ? null : (
          <label className={styles.uploadButton} htmlFor={inputId}>
            업로드
          </label>
        )}
      </div>

      {/*
        파일 선택은 네이티브 input이 열어야 하므로 버튼으로 대체하지 않고
        input 자체를 화면에서만 숨긴다. label의 htmlFor가 접근 가능한 이름을 준다.
      */}
      <input
        className={styles.input}
        id={inputId}
        type="file"
        accept="image/*"
        aria-label={ITEM_IMAGE_LABEL}
        aria-describedby={error ? errorId : undefined}
        onChange={(event) => {
          const file = event.target.files?.[0];
          // 같은 파일을 지웠다가 다시 골라도 change가 오도록 값을 비운다.
          event.target.value = '';
          if (file) {
            onSelect(file);
          }
        }}
      />

      {previewUrl ? (
        <div className={styles.preview}>
          <img className={styles.previewImage} src={previewUrl} alt="첨부한 물품 상태 사진" />
          <button
            className={styles.removeButton}
            type="button"
            aria-label="사진 삭제"
            onClick={onRemove}
          >
            <img className={styles.removeIcon} src={closeIcon} alt="" />
          </button>
        </div>
      ) : null}

      {error ? (
        <p className={styles.error} id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
