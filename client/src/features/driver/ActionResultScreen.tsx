import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { formatWon } from '../../model/furniture';
import { Button } from '../../ui/components';
import { Icon, type IconName } from '../../ui/Icon';
import styles from './actionResult.module.css';

export type ActionResultContent = {
    icon: IconName;
    workingTitle: string;
    title: string;
    description: string;
    /** 완료 후 0원부터 올라가며 보여줄 금액 */
    amount?: number;
    rows: ReadonlyArray<{ label: string; value: string }>;
};

type ActionResultScreenProps = {
    content: ActionResultContent;
    isDone: boolean;
    onConfirm: () => void;
};

/**
 * 배송원이 배차를 수락하거나 상태를 바꿀 때 화면 전체에 띄우는 진행·완료 화면.
 * 처리 중에는 링이 돌고, 끝나면 원이 채워지며 체크와 결과를 보여준 뒤 확인을 눌러 닫는다.
 */
export function ActionResultScreen({ content, isDone, onConfirm }: ActionResultScreenProps) {
    const footRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        // 결과 화면이 떠 있는 동안 뒤 화면이 스크롤되지 않게 한다
        const previous = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            document.body.style.overflow = previous;
        };
    }, []);

    useEffect(() => {
        if (isDone) footRef.current?.querySelector('button')?.focus({ preventScroll: true });
    }, [isDone]);

    return createPortal(
        <div aria-busy={!isDone} aria-modal className={`${styles.layer} ${isDone ? styles['layer--done'] : ''}`} role="dialog">
            <div className={styles.body}>
                <div className={styles.mark}>
                    <svg aria-hidden className={styles.ring} viewBox="0 0 120 120">
                        <circle className={styles.ring__track} cx="60" cy="60" r="54" />
                        <circle className={styles.ring__arc} cx="60" cy="60" r="54" />
                    </svg>
                    <span aria-hidden className={styles.ripple} />
                    <span aria-hidden className={styles.ripple} />
                    <div className={styles.core}>
                        <Icon className={styles.glyph} name={content.icon} size={44} />
                        <svg aria-hidden className={styles.check} viewBox="0 0 24 24">
                            <path d="M5.5 12.5l4.2 4.2L18.5 8" />
                        </svg>
                    </div>
                </div>
                <div aria-live="polite" className={styles.text}>
                    <p className={`${styles.title} type-headline-small-em`} key={isDone ? 'done' : 'working'}>
                        {isDone ? content.title : content.workingTitle}
                    </p>
                    <p className={`${styles.description} type-body-medium`} key={isDone ? 'done-description' : 'working-description'}>
                        {isDone ? content.description : '잠시만 기다려 주세요'}
                    </p>
                </div>
                {isDone && content.amount !== undefined && <CountUpAmount amount={content.amount} />}
                {isDone && content.rows.length > 0 && (
                    <dl className={styles.rows}>
                        {content.rows.map((row) => (
                            <div className={styles.row} key={row.label}>
                                <dt className="type-body-medium">{row.label}</dt>
                                <dd className="type-body-medium-em">{row.value}</dd>
                            </div>
                        ))}
                    </dl>
                )}
            </div>
            <div className={styles.foot} ref={footRef}>
                <Button disabled={!isDone} fullWidth onClick={onConfirm} size="medium">확인</Button>
            </div>
        </div>,
        document.body
    );
}

const COUNT_UP_MILLIS = 700;

function CountUpAmount({ amount }: { amount: number }) {
    const [shown, setShown] = useState(() => (prefersReducedMotion() ? amount : 0));
    useEffect(() => {
        if (prefersReducedMotion()) return undefined;
        const start = performance.now();
        let frame = requestAnimationFrame(function tick(now) {
            const progress = Math.min(1, (now - start) / COUNT_UP_MILLIS);
            const eased = 1 - Math.pow(1 - progress, 3);
            setShown(progress < 1 ? Math.round((amount * eased) / 100) * 100 : amount);
            if (progress < 1) frame = requestAnimationFrame(tick);
        });
        return () => cancelAnimationFrame(frame);
    }, [amount]);
    return <p className={`${styles.amount} type-headline-small-em`}>{formatWon(shown)}</p>;
}

function prefersReducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
