import MobileScreen from '../components/MobileScreen';
import NavBar from '../components/NavBar';
import PrimaryButton from '../components/PrimaryButton';
import { DISPATCH_PRIVACY_CONSENT_NOTICE } from '../model/privacyConsentNotice';
import styles from './PrivacyConsentNoticeScreen.module.css';

interface PrivacyConsentNoticeScreenProps {
  onBack: () => void;
  /** 동의하고 폼으로 돌아간다. */
  onAgree: () => void;
}

/** 시안 `개인정보 수집·이용 동의` 안내 화면 */
export default function PrivacyConsentNoticeScreen({
  onBack,
  onAgree,
}: PrivacyConsentNoticeScreenProps) {
  return (
    <MobileScreen
      header={<NavBar title="개인정보 수집·이용 동의" onBack={onBack} />}
      footer={
        <div className={styles.footer}>
          <PrimaryButton type="button" onClick={onAgree}>
            동의하고 계속하기
          </PrimaryButton>
        </div>
      }
    >
      <div className={styles.content}>
        <h2 className={styles.title}>
          {DISPATCH_PRIVACY_CONSENT_NOTICE.title.map((line) => (
            <span className={styles.titleLine} key={line}>
              {line}
            </span>
          ))}
        </h2>
        <dl className={styles.sections}>
          {DISPATCH_PRIVACY_CONSENT_NOTICE.sections.map((section) => (
            <div className={styles.section} key={section.label}>
              <dt className={styles.sectionLabel}>{section.label}</dt>
              <dd className={section.emphasis ? styles.sectionTextStrong : styles.sectionText}>
                {section.text}
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </MobileScreen>
  );
}
