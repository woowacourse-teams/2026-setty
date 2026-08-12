import { render, screen } from '@testing-library/react';
import PrivacyConsentNoticeScreen from '@/flows/dispatch/screens/PrivacyConsentNoticeScreen';
import { DISPATCH_PRIVACY_POLICY } from '@/flows/dispatch/model/dispatchPrivacyPolicy';

/**
 * 배차 동의 안내 화면이 개인정보 처리 안내 항목을 한 화면에서 모두 보여주는지 확인한다.
 * 개인정보를 남기지 않도록 실제 사용자 값은 쓰지 않는다.
 */
describe('배차 개인정보 수집·이용 동의 안내', () => {
  const renderScreen = () =>
    render(<PrivacyConsentNoticeScreen onBack={jest.fn()} onAgree={jest.fn()} />);

  it('처리 안내에 필요한 항목을 모두 안내한다', () => {
    renderScreen();

    [
      '처리 주체',
      '수집 항목',
      '요청 처리 중 생성·저장되는 정보',
      '이용 목적',
      '보유·이용 기간',
      '동의를 거부하면',
      '철회·삭제 요청과 파기',
      '문의',
      '안내문 정보',
    ].forEach((label) => {
      expect(screen.getByText(label)).toBeInTheDocument();
    });
  });

  it('수집 항목·보유 기간·문의 채널·안내문 버전을 정책 값 그대로 보여준다', () => {
    renderScreen();

    expect(screen.getByText(DISPATCH_PRIVACY_POLICY.items)).toBeInTheDocument();
    expect(screen.getByText(DISPATCH_PRIVACY_POLICY.purpose)).toBeInTheDocument();
    expect(screen.getByText(DISPATCH_PRIVACY_POLICY.contactEmail)).toBeInTheDocument();
    expect(
      screen.getByText(new RegExp(DISPATCH_PRIVACY_POLICY.retentionPeriod)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(new RegExp(DISPATCH_PRIVACY_POLICY.version)),
    ).toBeInTheDocument();
  });
});
