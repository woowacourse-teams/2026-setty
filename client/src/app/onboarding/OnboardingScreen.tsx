import { Fragment } from 'react';
import logoCheck from './assets/logo-check.svg';
import {
  ONBOARDING_STEP_COUNT,
  ONBOARDING_STEPS,
  type OnboardingStep,
} from './onboardingSteps';
import styles from './OnboardingScreen.module.css';

interface OnboardingScreenProps {
  /** 1부터 시작하는 현재 단계 번호 */
  stepNumber: number;
  /** 현재 단계의 카피 */
  step: OnboardingStep;
  /** 마지막 단계가 아닐 때 다음 단계로 이동한다. */
  onNext: () => void;
  /** 마지막 단계가 아닐 때 온보딩을 끝내고 홈으로 이동한다. */
  onSkip: () => void;
  /** 마지막 단계에서 예상 견적 진입점으로 이동한다. */
  onCheckEstimate: () => void;
  /** 마지막 단계에서 배차 요청(거래 링크 만들기)으로 이동한다. */
  onCreateLink: () => void;
}

/** 시안의 첫 진입 서비스 소개 화면 */
export default function OnboardingScreen({
  stepNumber,
  step,
  onNext,
  onSkip,
  onCheckEstimate,
  onCreateLink,
}: OnboardingScreenProps) {
  const isLastStep = stepNumber === ONBOARDING_STEP_COUNT;

  return (
    <div className={styles.screen}>
      <header className={styles.header}>
        <span className={styles.brand}>
          <span className={styles.mark}>
            <img className={styles.markIcon} src={logoCheck} alt="" />
          </span>
          <span className={styles.name}>SETTY</span>
        </span>
        {/* 마지막 단계에는 이어질 화면이 없어 시안에도 건너뛰기가 없다. */}
        {isLastStep ? null : (
          <button className={styles.skip} type="button" onClick={onSkip}>
            건너뛰기
          </button>
        )}
      </header>

      <div className={styles.body}>
        <h1 className={styles.title}>
          {step.titleLines.map((line, lineIndex) => (
            <Fragment key={line}>
              {lineIndex === 0 ? null : <br />}
              {line}
            </Fragment>
          ))}
        </h1>
        <p className={styles.description}>{step.description}</p>
      </div>

      <div className={styles.footer}>
        <ol
          className={styles.indicator}
          aria-label={`전체 ${ONBOARDING_STEP_COUNT}단계 중 ${stepNumber}단계`}
        >
          {ONBOARDING_STEPS.map((_, index) => (
            <li
              className={[styles.dot, index + 1 === stepNumber ? styles.dotCurrent : '']
                .filter(Boolean)
                .join(' ')}
              key={index}
            />
          ))}
        </ol>

        {isLastStep ? (
          <>
            <button className={styles.primary} type="button" onClick={onCheckEstimate}>
              예상 견적 확인하기
            </button>
            <button className={styles.secondary} type="button" onClick={onCreateLink}>
              거래 링크 만들어보기
              <span className={styles.chevron} aria-hidden="true" />
            </button>
          </>
        ) : (
          <button className={styles.primary} type="button" onClick={onNext}>
            다음
          </button>
        )}
      </div>
    </div>
  );
}
