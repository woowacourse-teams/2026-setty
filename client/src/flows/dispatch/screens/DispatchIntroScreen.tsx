import BrandHeader from '../components/BrandHeader';
import MobileScreen from '../components/MobileScreen';
import PrimaryButton from '../components/PrimaryButton';
import TextButton from '../components/TextButton';
import styles from './DispatchIntroScreen.module.css';

interface DispatchIntroScreenProps {
  /** 구매자가 배차 요청(거래 링크 만들기)을 시작한다. */
  onCreateLink: () => void;
  /** 예상 견적 요청 진입점으로 이동한다. */
  onCheckEstimate: () => void;
}

interface IntroBenefit {
  title: string;
  description: string;
}

/**
 * 절차(STEP 1·2·3) 대신 SETTY가 대신 해주는 것을 먼저 보여준다.
 * 카피는 결제·에스크로처럼 MVP 범위 밖 표현을 쓰지 않고
 * 운영자가 실제로 수행하는 개인정보 중계와 배송 조율만 말한다.
 */
const INTRO_BENEFITS: IntroBenefit[] = [
  {
    title: '번호도 주소도 서로 몰라도 돼요',
    description: '연락처·집주소는 SETTY만 알고 안전하게 연결해요',
  },
  {
    title: '큰 가구도 용달까지 한 번에',
    description: '배송 차량 섭외부터 픽업·전달까지 SETTY가',
  },
];

/** 배차 흐름의 홈·온보딩 화면 */
export default function DispatchIntroScreen({
  onCreateLink,
  onCheckEstimate,
}: DispatchIntroScreenProps) {
  return (
    <MobileScreen
      header={<BrandHeader />}
      footer={
        <>
          <PrimaryButton onClick={onCreateLink}>거래 링크 만들기</PrimaryButton>
          <TextButton onClick={onCheckEstimate}>예상 견적 확인하기</TextButton>
        </>
      }
    >
      <div className={styles.content}>
        <h1 className={styles.title}>
          번거로운 중고 가구 거래,
          <br />
          SETTY가 도와드려요
        </h1>
        <p className={styles.subtitle}>
          개인정보 노출도, 지루한 대화도 없이. 조건만 정하면 거래가 끝까지 이어져요.
        </p>

        <ul className={styles.benefits}>
          {INTRO_BENEFITS.map((benefit) => (
            <li className={styles.benefit} key={benefit.title}>
              <p className={styles.benefitTitle}>{benefit.title}</p>
              <p className={styles.benefitDescription}>{benefit.description}</p>
            </li>
          ))}
        </ul>
      </div>
    </MobileScreen>
  );
}
