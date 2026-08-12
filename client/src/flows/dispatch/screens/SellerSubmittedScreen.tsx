import BrandHeader from '../components/BrandHeader';
import MobileScreen from '../components/MobileScreen';
import PrimaryButton from '../components/PrimaryButton';
import ResultMessage from '../components/ResultMessage';

interface SellerSubmittedScreenProps {
  /** 홈으로 돌아가 흐름을 종료 */
  onGoHome: () => void;
}

/**
 * 판매자가 발송 정보를 제출한 직후의 종료 화면이다.
 * 서버를 호출하지 않고, 구매자 등 거래 상대방 정보도 표시하지 않는다.
 * 결과 안내 문자는 운영자가 직접 보내는 수동 절차다.
 */
export default function SellerSubmittedScreen({ onGoHome }: SellerSubmittedScreenProps) {
  return (
    <MobileScreen
      header={<BrandHeader />}
      footer={<PrimaryButton onClick={onGoHome}>홈으로 돌아가기</PrimaryButton>}
    >
      <ResultMessage
        emoji="⏳"
        title="정보가 제출됐어요"
        description={'물품을 확인한 뒤\n결과를 문자(SMS)로 안내해 드릴게요.'}
      />
    </MobileScreen>
  );
}
