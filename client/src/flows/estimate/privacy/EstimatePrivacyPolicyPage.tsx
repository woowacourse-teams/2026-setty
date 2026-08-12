import { Link } from 'react-router-dom';
import { ESTIMATE_PRIVACY_POLICY } from './estimatePrivacyPolicy';
import styles from './EstimatePrivacy.module.css';

export default function EstimatePrivacyPolicyPage() {
  return (
    <main className={styles.policyPage}>
      <article className={styles.policyCard}>
        <header>
          <p className={styles.policyBrand}>SETTY</p>
          <h1>예상 견적 개인정보 처리 안내</h1>
          <p className={styles.policyIntro}>
            {ESTIMATE_PRIVACY_POLICY.controller}은 예상 견적 요청을 처리하기 위해 필요한
            개인정보만 수집·이용합니다.
          </p>
        </header>

        <section className={styles.policySection}>
          <h2>1. 처리 목적과 처리 정보</h2>
          <ul>
            <li>목적: {ESTIMATE_PRIVACY_POLICY.purpose}</li>
            <li>사용자 입력 항목: {ESTIMATE_PRIVACY_POLICY.items}</li>
            <li>
              요청 처리 중 생성·저장되는 정보: {ESTIMATE_PRIVACY_POLICY.generatedItems}
            </li>
          </ul>
        </section>

        <section className={styles.policySection}>
          <h2>2. 보유·이용 기간</h2>
          <p>
            수집한 개인정보는 <strong>{ESTIMATE_PRIVACY_POLICY.retentionPeriod}</strong>{' '}
            보관한 뒤 삭제합니다. 동의 철회나 삭제 요청이 먼저 처리되면 그 시점에
            개인정보를 삭제합니다.
          </p>
        </section>

        <section className={styles.policySection}>
          <h2>3. 동의 거부</h2>
          <p>
            개인정보 수집·이용에 동의하지 않을 수 있지만, 이름과 연락처가 없으면 요청을
            식별하고 문자로 견적을 안내할 수 없어 예상 견적 요청이 접수되지 않습니다.
          </p>
        </section>

        <section className={styles.policySection}>
          <h2>4. 철회·삭제 요청과 파기</h2>
          <p>
            동의 철회나 삭제는{' '}
            <a href={`mailto:${ESTIMATE_PRIVACY_POLICY.contactEmail}`}>
              {ESTIMATE_PRIVACY_POLICY.contactEmail}
            </a>
            로 요청할 수 있습니다. 운영팀이 요청을 확인해 수동으로 처리하고 처리 결과를
            기록합니다. 요청에 연결된 운영 기록을 포함한 전자적 파일은 복구할 수 없는
            방법으로 삭제하며, 삭제 후에는 특정 개인을 알아볼 수 없는 통계만 남길 수
            있습니다.
          </p>
        </section>

        <section className={styles.policySection}>
          <h2>5. 처리 주체와 문의</h2>
          <ul>
            <li>처리 주체: {ESTIMATE_PRIVACY_POLICY.controller}</li>
            <li>
              문의:{' '}
              <a href={`mailto:${ESTIMATE_PRIVACY_POLICY.contactEmail}`}>
                {ESTIMATE_PRIVACY_POLICY.contactEmail}
              </a>
            </li>
          </ul>
        </section>

        <section className={styles.policySection}>
          <h2>6. 안내문 정보</h2>
          <ul>
            <li>적용일: {ESTIMATE_PRIVACY_POLICY.effectiveDate}</li>
            <li>버전: {ESTIMATE_PRIVACY_POLICY.version}</li>
          </ul>
        </section>

        <Link className={styles.backLink} to="/estimate">
          견적 요청으로 돌아가기
        </Link>
      </article>
    </main>
  );
}
