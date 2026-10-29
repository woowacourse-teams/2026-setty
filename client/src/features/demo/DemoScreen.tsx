import { useState } from 'react';
import { deliveryTokenStore, tokenStore } from '../../api/http';
import { routes } from '../../navigation';
import { Button, ToggleButtonGroup } from '../../ui/components';
import styles from './demo.module.css';

// 데모데이 피드백 설문 주소. 비워 두면 버튼을 숨긴다.
const FEEDBACK_URL = '';

type Side = 'buyer' | 'driver';

const sides: readonly Side[] = ['buyer', 'driver'];
const sideLabels: Record<Side, string> = { buyer: '구매자', driver: '배송원' };
const sidePaths: Record<Side, string> = { buyer: routes.home, driver: routes.driverRequests };

const steps: Array<{ who: string; text: string }> = [
    { who: '구매자', text: '가구를 골라 결제해요' },
    { who: '배송원', text: '배차 요청을 수락해요' },
    { who: '배송원', text: '판매자에게 가구를 픽업해요' },
    { who: '배송원', text: '배송을 완료해요' }
];

/**
 * 데모데이에서 구매자와 배송원 화면을 나란히 띄워 주문부터 배송 완료까지의 흐름을 한 번에 보여준다.
 * 두 화면은 같은 사이트를 iframe으로 띄우고, 회원과 배송원 토큰을 따로 저장하므로 각자 로그인한 상태를 유지한다.
 */
export function DemoScreen() {
    const [visibleSide, setVisibleSide] = useState<Side>('buyer');
    // 바꾸면 두 iframe을 처음부터 다시 띄운다
    const [session, setSession] = useState(0);

    const restart = () => {
        tokenStore.clear();
        deliveryTokenStore.clear();
        setSession((current) => current + 1);
    };

    return (
        <div className={styles.page}>
            <header className={styles.header}>
                <div className={styles.header__text}>
                    <h1 className="type-headline-small-em">SETTY 체험하기</h1>
                    <p className={`${styles.header__note} type-body-medium`}>
                        왼쪽은 구매자, 오른쪽은 배송원 화면이에요. 내가 올린 가구는 직접 살 수 없으니 다른 사람의 가구를 골라주세요.
                    </p>
                </div>
                <div className={styles.header__actions}>
                    {FEEDBACK_URL && (
                        <Button onClick={() => window.open(FEEDBACK_URL, '_blank', 'noopener')} variant="tonal">피드백 남기기</Button>
                    )}
                    <Button onClick={restart} variant="outlined">처음부터 다시</Button>
                </div>
            </header>
            <ol className={styles.steps}>
                {steps.map((step, index) => (
                    <li className={styles.step} key={step.text}>
                        <span className={`${styles.step__number} type-label-medium`}>{index + 1}</span>
                        <span className="type-label-large">
                            <span className={styles.step__who}>{step.who} · </span>
                            {step.text}
                        </span>
                    </li>
                ))}
            </ol>
            <div className={styles.switcher}>
                <ToggleButtonGroup
                    ariaLabel="보여줄 화면"
                    labelOf={(side) => sideLabels[side]}
                    onSelect={setVisibleSide}
                    options={sides}
                    selected={visibleSide}
                />
            </div>
            <div className={styles.frames}>
                {sides.map((side) => (
                    <section className={`${styles.frame} ${side === visibleSide ? '' : styles['frame--hidden']}`} key={side}>
                        <div className={styles.frame__label}>
                            <span className="type-title-medium-em">{sideLabels[side]}</span>
                            <a className={`${styles.frame__open} type-label-large`} href={sidePaths[side]} rel="noreferrer" target="_blank">새 탭에서 열기</a>
                        </div>
                        <div className={styles.phone}>
                            <iframe key={session} src={sidePaths[side]} title={`${sideLabels[side]} 화면`} />
                        </div>
                    </section>
                ))}
            </div>
        </div>
    );
}
