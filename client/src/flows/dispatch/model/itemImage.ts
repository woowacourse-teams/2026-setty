import type { RefObject } from 'react';

/** 구매자·판매자 화면이 함께 쓰는 `물품 상태 사진` 첨부 상태다. */
export interface SelectedItemImage {
  file: File;
  /** `URL.createObjectURL`로 만든 미리보기 주소 */
  previewUrl: string;
}

export const ITEM_IMAGE_TYPE_ERROR = '이미지 파일만 첨부할 수 있어요.';

/** 미리보기 URL을 해제해 blob이 탭에 남지 않게 한다. */
export const releaseItemImagePreview = (ref: RefObject<string | null>) => {
  if (ref.current) {
    URL.revokeObjectURL(ref.current);
    ref.current = null;
  }
};
