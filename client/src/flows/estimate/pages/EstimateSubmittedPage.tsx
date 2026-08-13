import { useNavigate } from 'react-router-dom';
import mailboxIcon from '@/flows/estimate/assets/result-mailbox.png';
import BrandHeader from '@/flows/estimate/components/BrandHeader';
import MobileScreen from '@/flows/estimate/components/MobileScreen';
import PrimaryButton from '@/flows/estimate/components/PrimaryButton';
import ResultMessage from '@/flows/estimate/components/ResultMessage';

/** 배차 flow의 홈 화면 경로다. estimate는 코드를 import하지 않고 경로만 참조한다. */
const HOME_PATH = '/';

/**
 * 예상 견적 요청이 접수된 직후의 종료 화면이다.
 * 서버를 호출하지 않고 금액도 계산하지 않는다.
 * 예상 금액 확인과 결과 안내 문자는 운영자가 직접 처리하는 수동 절차다.
 */
export default function EstimateSubmittedPage() {
  const navigate = useNavigate();

  return (
    <MobileScreen
      header={<BrandHeader />}
      footer={
        <PrimaryButton onClick={() => navigate(HOME_PATH)}>홈으로 돌아가기</PrimaryButton>
      }
    >
      <ResultMessage
        iconSrc={mailboxIcon}
        title="견적 요청이 접수됐어요"
        description={'예상 금액을 확인한 뒤\n결과를 문자(SMS)로 안내해 드릴게요.'}
      />
    </MobileScreen>
  );
}
