import BrandHeader from '../components/BrandHeader';
import MobileScreen from '../components/MobileScreen';
import PrimaryButton from '../components/PrimaryButton';
import styles from './SellerIntroScreen.module.css';

interface SellerIntroScreenProps {
  /** 판매자 입력 폼으로 이동한다. */
  onProceed: () => void;
}

interface SellerIntroNotice {
  title: string;
  description: string;
}

/**
 * 판매자는 구매자에게 링크만 받고 들어오므로 SETTY를 모르는 상태다.
 * 카피는 DEC-017의 정보 공개 범위와 MVP 범위 안에서만 말한다.
 * 결제·에스크로를 하지 않는다는 사실을 그대로 알리고 새로운 약속을 만들지 않는다.
 */
const SELLER_INTRO_NOTICES: SellerIntroNotice[] = [
  {
    title: '구매자에게 연락처·주소를 알려주지 않아요',
    description: '회수 배차에만 쓰이고, 상대에게 공개되지 않아요',
  },
  {
    title: '결제나 계좌 정보는 묻지 않아요',
    description: 'SETTY는 카드번호·비밀번호를 요구하지 않아요',
  },
  {
    title: '용달 섭외까지 SETTY가 대신해요',
    description: '입력만 마치면 픽업 일정을 문자로 안내해요',
  },
];

/** 시안 `판매자 링크 진입` — 입력 폼 앞에서 SETTY와 정보 사용처를 먼저 설명한다. */
export default function SellerIntroScreen({ onProceed }: SellerIntroScreenProps) {
  return (
    <MobileScreen
      header={<BrandHeader />}
      footer={
        <PrimaryButton type="button" onClick={onProceed}>
          진행하기
        </PrimaryButton>
      }
    >
      <div className={styles.content}>
        <h1 className={styles.title}>
          <span className={styles.brand}>SETTY</span>로 거래가 요청됐어요
        </h1>
        <p className={styles.subtitle}>
          SETTY는 중고 거래에서 번거로운 배송을 대신 해결해주는 서비스예요. 개인정보 노출
          없이 용달 섭외까지 SETTY가 맡아요.
        </p>

        <h2 className={styles.noticesTitle}>입력한 정보는 이렇게 쓰여요</h2>
        <ul className={styles.notices}>
          {SELLER_INTRO_NOTICES.map((notice) => (
            <li className={styles.notice} key={notice.title}>
              <p className={styles.noticeTitle}>{notice.title}</p>
              <p className={styles.noticeDescription}>{notice.description}</p>
            </li>
          ))}
        </ul>
      </div>
    </MobileScreen>
  );
}
