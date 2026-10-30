import {
    useEffect,
    useId,
    useRef,
    useState,
    type ButtonHTMLAttributes,
    type CSSProperties,
    type ReactNode,
    type RefObject
} from 'react';
import { createPortal } from 'react-dom';
import { tradeStatusLabel, type OrderState, type SaleStatus } from '../model/furniture';
import { Icon, type IconName } from './Icon';
import { shapeStyle, type ShapeName } from './shapes';
import { dismissSnackbar, useSnackbar } from './snackbar';
import styles from './components.module.css';

function cx(...names: Array<string | false | null | undefined>): string {
    return names.filter(Boolean).join(' ');
}

// ---------- 화면 뼈대 ----------

type ScaffoldProps = {
    topBar?: ReactNode;
    bottomBar?: ReactNode;
    floatingActionButton?: ReactNode;
    children: ReactNode;
};

/** 상단 바, 본문, 화면 아래에 고정되는 버튼 영역과 스낵바를 배치한다. */
export function Scaffold({ topBar, bottomBar, floatingActionButton, children }: ScaffoldProps) {
    const snackbar = useSnackbar();
    return (
        <div className={styles.scaffold}>
            {topBar}
            <main className={styles.scaffold__content}>{children}</main>
            {bottomBar && (
                <>
                    {/* 고정된 아래 버튼이 본문 끝을 가리지 않게 같은 높이만큼 비워 둔다 */}
                    <div aria-hidden className={styles['scaffold__bottom-spacer']} style={{ height: 'calc(80px + var(--safe-bottom))' }} />
                    <div className={cx(styles['fixed-column'], styles['bottom-bar'])}>{bottomBar}</div>
                </>
            )}
            {floatingActionButton}
            {snackbar && (
                <div
                    aria-live="polite"
                    className={cx(styles['fixed-column'], styles['snackbar-host'], bottomBar && styles['snackbar-host--above-bar'])}
                    role="status"
                >
                    <div className={styles.snackbar} key={snackbar.id}>
                        <span className={cx(styles.snackbar__message, 'type-body-medium')}>{snackbar.message}</span>
                        {snackbar.actionLabel && (
                            <button
                                className={cx(styles.snackbar__action, 'type-label-large')}
                                onClick={() => {
                                    dismissSnackbar();
                                    snackbar.onAction?.();
                                }}
                                type="button"
                            >
                                {snackbar.actionLabel}
                            </button>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

/** 화면 아래에 붙는 주 동작 영역. [wide]면 로그인·가입 화면처럼 좌우 여백을 넓힌다. */
export function BottomBar({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
    return <div className={cx(styles['bottom-bar__inner'], wide && styles['bottom-bar__inner--wide'])}>{children}</div>;
}

// ---------- 상단 바 ----------

function useScrolled(sentinel: RefObject<HTMLElement | null>, fallbackThreshold = 0) {
    const [isScrolled, setIsScrolled] = useState(false);
    useEffect(() => {
        const target = sentinel.current;
        if (target) {
            const observer = new IntersectionObserver(
                ([entry]) => setIsScrolled(!entry.isIntersecting),
                { rootMargin: '-64px 0px 0px 0px' }
            );
            observer.observe(target);
            return () => observer.disconnect();
        }
        const onScroll = () => setIsScrolled(window.scrollY > fallbackThreshold);
        onScroll();
        window.addEventListener('scroll', onScroll, { passive: true });
        return () => window.removeEventListener('scroll', onScroll);
    }, [sentinel, fallbackThreshold]);
    return isScrolled;
}

type TopAppBarProps = {
    title: ReactNode;
    subtitle?: ReactNode;
    navigation?: ReactNode;
    actions?: ReactNode;
    height?: number;
};

/** 스크롤해도 높이가 바뀌지 않는 상단 바. */
export function TopAppBar({ title, subtitle, navigation, actions, height = 64 }: TopAppBarProps) {
    const noSentinel = useRef<HTMLElement | null>(null);
    const isScrolled = useScrolled(noSentinel);
    return (
        <header className={cx(styles['top-bar'], isScrolled && styles['top-bar--scrolled'])} style={{ minHeight: height }}>
            {navigation && <div className={styles['top-bar__navigation']}>{navigation}</div>}
            <div className={styles['top-bar__title']}>
                <div className={styles['top-bar__title-text']}>{title}</div>
                {subtitle && <div className={cx(styles['top-bar__subtitle'], 'type-body-medium-em')}>{subtitle}</div>}
            </div>
            {actions && <div className={styles['top-bar__actions']}>{actions}</div>}
        </header>
    );
}

type FlexibleTopAppBarProps = {
    title: ReactNode;
    // 접힌 상단 바에 보여줄 제목. 없으면 [title]을 쓴다
    collapsedTitle?: ReactNode;
    subtitle?: ReactNode;
    navigation?: ReactNode;
    actions?: ReactNode;
    size?: 'large' | 'medium';
};

/** 큰 제목이 본문과 함께 스크롤되어 사라지면 상단 바에 작은 제목을 보여준다. */
export function FlexibleTopAppBar({ title, collapsedTitle, subtitle, navigation, actions, size = 'large' }: FlexibleTopAppBarProps) {
    const titleRef = useRef<HTMLDivElement | null>(null);
    const isScrolled = useScrolled(titleRef);
    return (
        <>
            <header className={cx(styles['top-bar'], isScrolled && styles['top-bar--scrolled'])}>
                {navigation && <div className={styles['top-bar__navigation']}>{navigation}</div>}
                <div aria-hidden={!isScrolled} className={cx(styles['top-bar__title'], styles['top-bar__title--collapsible'])}>
                    <div className={styles['top-bar__title-text']}>{collapsedTitle ?? title}</div>
                </div>
                {actions && <div className={styles['top-bar__actions']}>{actions}</div>}
            </header>
            <div className={cx(styles['flexible-title'], styles[`flexible-title--${size}`])} ref={titleRef}>
                <h1 className={styles['flexible-title__text']} style={{ margin: 0 }}>{title}</h1>
                {subtitle && <div className={styles['flexible-title__subtitle']}>{subtitle}</div>}
            </div>
        </>
    );
}

// ---------- 버튼 ----------

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: 'filled' | 'tonal' | 'danger-tonal' | 'outlined' | 'text';
    size?: 'small' | 'medium';
    fullWidth?: boolean;
};

export function Button({ variant = 'filled', size = 'small', fullWidth = false, className, type = 'button', ...props }: ButtonProps) {
    return (
        <button
            className={cx(
                styles.button,
                styles[`button--${variant}`],
                size === 'medium' && styles['button--medium'],
                fullWidth && styles['button--full'],
                className
            )}
            type={type}
            {...props}
        />
    );
}

type IconButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
    icon: IconName;
    label: string;
    variant?: 'standard' | 'tonal' | 'tonal-off';
    iconSize?: number;
    small?: boolean;
    iconStyle?: CSSProperties;
};

export function IconButton({ icon, label, variant = 'standard', iconSize = 24, small = false, iconStyle, className, ...props }: IconButtonProps) {
    return (
        <button
            aria-label={label}
            className={cx(
                styles['icon-button'],
                variant !== 'standard' && styles[`icon-button--${variant}`],
                small && styles['icon-button--small'],
                className
            )}
            type="button"
            {...props}
        >
            <span style={{ display: 'flex', ...iconStyle }}><Icon name={icon} size={iconSize} /></span>
        </button>
    );
}

export function BackIconButton({ onBack, disabled }: { onBack: () => void; disabled?: boolean }) {
    return <IconButton disabled={disabled} icon="arrowBack" label="뒤로 가기" onClick={onBack} variant="tonal" />;
}

type ExtendedFabProps = {
    label: string;
    icon: IconName;
    expanded: boolean;
    onClick: () => void;
};

export function ExtendedFab({ label, icon, expanded, onClick }: ExtendedFabProps) {
    return (
        <div className={cx(styles['fixed-column'], styles.fab)}>
            <button
                aria-label={label}
                className={cx(styles.fab__button, !expanded && styles['fab__button--collapsed'])}
                onClick={onClick}
                type="button"
            >
                <Icon name={icon} size={24} />
                <span className={styles.fab__label}>{label}</span>
            </button>
        </div>
    );
}

// ---------- 로딩·안내 ----------

export function LoadingIndicator() {
    return (
        <span aria-label="불러오는 중" className={styles.loading} role="progressbar">
            <span className={styles.loading__shape} style={shapeStyle('cookie9')} />
        </span>
    );
}

export function LoadingContent() {
    return (
        <div className={styles['loading-content']}>
            <LoadingIndicator />
        </div>
    );
}

type SettyMessageProps = {
    title: string;
    description?: string;
    shape?: ShapeName;
    shapeColor?: string;
    action?: ReactNode;
    style?: CSSProperties;
};

/** 빈 목록·에러처럼 화면 가운데에 보여주는 안내. */
export function SettyMessage({ title, description, shape = 'cookie9', shapeColor = 'var(--tertiary-container)', action, style }: SettyMessageProps) {
    return (
        <div className={styles.message} style={style}>
            <div aria-hidden className={styles.message__shape} style={{ ...shapeStyle(shape), background: shapeColor }} />
            <p className={cx(styles.message__title, 'type-headline-small-em')}>{title}</p>
            {description && <p className={cx(styles.message__description, 'type-body-medium-em')}>{description}</p>}
            {action}
        </div>
    );
}

type ErrorContentProps = {
    message: string;
    isUnauthorized: boolean;
    onRetry: () => void;
    onRequireLogin: () => void;
};

/** 불러오기 실패 화면. 로그인이 풀린 경우 로그인 화면으로 보낸다. */
export function ErrorContent({ message, isUnauthorized, onRetry, onRequireLogin }: ErrorContentProps) {
    return (
        <div className={styles['center-fill']}>
            <SettyMessage
                action={(
                    <Button onClick={isUnauthorized ? onRequireLogin : onRetry}>
                        {isUnauthorized ? '로그인하기' : '다시 시도'}
                    </Button>
                )}
                shape="softBurst"
                shapeColor="var(--error-container)"
                title={message}
            />
        </div>
    );
}

export function CenterFill({ children }: { children: ReactNode }) {
    return <div className={styles['center-fill']}>{children}</div>;
}

export function SectionTitle({ children, style }: { children: ReactNode; style?: CSSProperties }) {
    return <h2 className={cx(styles['section-title'], 'type-title-medium-em')} style={style}>{children}</h2>;
}

// ---------- 목록 ----------

export function SegmentedList({ children, style }: { children: ReactNode; style?: CSSProperties }) {
    return <ul className={styles['segmented-list']} style={style}>{children}</ul>;
}

type SegmentedListItemProps = {
    index: number;
    count: number;
    headline: ReactNode;
    leading?: ReactNode;
    overline?: ReactNode;
    supporting?: ReactNode;
    trailing?: ReactNode;
    onClick?: () => void;
    style?: CSSProperties;
};

/** 붙어 있는 목록 항목. 첫 항목은 위 모서리를, 마지막 항목은 아래 모서리를 크게 둥글린다. */
export function SegmentedListItem({ index, count, headline, leading, overline, supporting, trailing, onClick, style }: SegmentedListItemProps) {
    const className = cx(
        styles['segmented-item'],
        index === 0 && styles['segmented-item--first'],
        index === count - 1 && styles['segmented-item--last'],
        onClick && styles['segmented-item--clickable']
    );
    const content = (
        <>
            {leading && <div className={styles['segmented-item__leading']}>{leading}</div>}
            <div className={styles['segmented-item__body']}>
                {overline && <div className={cx(styles['segmented-item__overline'], 'type-label-medium')}>{overline}</div>}
                <div className={styles['segmented-item__headline']}>{headline}</div>
                {supporting && <div className={styles['segmented-item__supporting']}>{supporting}</div>}
            </div>
        </>
    );
    return (
        <li style={{ listStyle: 'none' }}>
            {/* 행 전체를 누르는 영역과 안쪽 버튼(찜·수정 등)이 겹치지 않도록 trailing은 바깥 레이어에 둔다 */}
            <div className={className} style={{ position: 'relative', ...style }}>
                {onClick ? (
                    <button
                        onClick={onClick}
                        style={{ display: 'flex', flex: 1, alignItems: 'center', gap: 16, minWidth: 0, textAlign: 'left' }}
                        type="button"
                    >
                        {content}
                    </button>
                ) : content}
                {trailing && <div className={styles['segmented-item__trailing']}>{trailing}</div>}
            </div>
        </li>
    );
}

type PlainListItemProps = {
    headline: ReactNode;
    supporting?: ReactNode;
    trailing?: ReactNode;
    onClick: () => void;
};

/** 시트 안의 단순한 목록 항목. */
export function PlainListItem({ headline, supporting, trailing, onClick }: PlainListItemProps) {
    return (
        <button className={styles['plain-item']} onClick={onClick} type="button">
            <span className={styles['plain-item__body']}>
                <span className="type-body-large">{headline}</span>
                {supporting && <span className={cx(styles['plain-item__supporting'], 'type-body-medium')}>{supporting}</span>}
            </span>
            {trailing}
        </button>
    );
}

// ---------- 썸네일·배지 ----------

type FurnitureThumbnailProps = {
    imageUrl: string | null | undefined;
    placeholder: string;
    alt?: string;
    size?: number;
};

export function FurnitureThumbnail({ imageUrl, placeholder, alt, size = 72 }: FurnitureThumbnailProps) {
    const [isBroken, setIsBroken] = useState(false);
    return (
        <div className={styles.thumbnail} style={{ width: size, height: size, ...shapeStyle('square') }}>
            <span className="type-label-medium">{placeholder}</span>
            {imageUrl && !isBroken && (
                <img alt={alt ?? ''} className={styles.thumbnail__image} loading="lazy" onError={() => setIsBroken(true)} src={imageUrl} />
            )}
        </div>
    );
}

type StatusBadgeProps = {
    text: string;
    containerColor: string;
    contentColor: string;
};

export function StatusBadge({ text, containerColor, contentColor }: StatusBadgeProps) {
    return (
        <span className={cx(styles.badge, 'type-label-large')} style={{ background: containerColor, color: contentColor }}>
            {text}
        </span>
    );
}

/** 목록 카드 오른쪽 열. 첫 자식은 위, 마지막 자식은 아래 오른쪽 끝에 놓인다. */
export function TrailingColumn({ children }: { children: ReactNode }) {
    return <div className={styles['trailing-column']}>{children}</div>;
}

/** 목록 카드 오른쪽 아래의 찜 수. 찜이 없으면 그리지 않는다. */
/** 매물을 찜한 사람 수. 내가 찜했는지와 헷갈리지 않게 빈 하트로 그린다. */
export function FavoriteCount({ count }: { count: number }) {
    if (count < 1) return null;
    return (
        <span aria-label={`찜 ${count}개`} className={cx(styles['favorite-count'], 'type-label-medium')}>
            <Icon name="favorite" size={14} />
            {count}
        </span>
    );
}

export function SaleStatusBadge({ saleStatus, orderState = 'NONE' }: { saleStatus: SaleStatus; orderState?: OrderState }) {
    let colors: [string, string];
    if (saleStatus === 'SOLD') {
        colors = ['var(--surface-container-highest)', 'var(--on-surface-variant)'];
    } else if (saleStatus === 'RESERVED' || orderState !== 'NONE') {
        colors = ['var(--tertiary-container)', 'var(--on-tertiary-container)'];
    } else {
        colors = ['var(--primary-container)', 'var(--on-primary-container)'];
    }
    return (
        <StatusBadge
            containerColor={colors[0]}
            contentColor={colors[1]}
            text={tradeStatusLabel(saleStatus, orderState)}
        />
    );
}

// ---------- 입력 ----------

type TextFieldProps = {
    label: string;
    value: string;
    onChange?: (value: string) => void;
    placeholder?: string;
    supportingText?: string;
    isError?: boolean;
    disabled?: boolean;
    readOnly?: boolean;
    onClick?: () => void;
    type?: 'text' | 'password' | 'tel' | 'search';
    inputMode?: 'text' | 'numeric' | 'tel' | 'search';
    autoComplete?: string;
    enterKeyHint?: 'next' | 'go' | 'done' | 'search';
    onEnter?: () => void;
    suffix?: string;
    leadingIcon?: IconName;
    trailing?: ReactNode;
    multiline?: boolean;
    autoFocus?: boolean;
    style?: CSSProperties;
};

/** 테두리가 있는 입력 칸. 값이 있거나 입력 중이면 라벨이 테두리 위로 올라간다. */
export function TextField({
    label,
    value,
    onChange,
    placeholder,
    supportingText,
    isError = false,
    disabled = false,
    readOnly = false,
    onClick,
    type = 'text',
    inputMode,
    autoComplete,
    enterKeyHint,
    onEnter,
    suffix,
    leadingIcon,
    trailing,
    multiline = false,
    autoFocus,
    style
}: TextFieldProps) {
    const id = useId();
    const [isFocused, setIsFocused] = useState(false);
    const isFloated = isFocused || value.length > 0;
    const sharedProps = {
        id,
        value,
        disabled,
        readOnly,
        autoFocus,
        'aria-invalid': isError || undefined,
        'aria-describedby': supportingText ? `${id}-supporting` : undefined,
        'aria-label': label ? undefined : placeholder,
        // 라벨이 칸 가운데 있는 동안에는 겹치지 않게 안내 문구를 숨긴다
        placeholder: isFloated || !label ? placeholder : undefined,
        onFocus: () => setIsFocused(!readOnly),
        onBlur: () => setIsFocused(false),
        onClick: readOnly ? onClick : undefined
    };
    return (
        <div
            className={cx(
                styles.field,
                isFocused && styles['field--focused'],
                isFloated && styles['field--floated'],
                isError && styles['field--error'],
                disabled && styles['field--disabled'],
                leadingIcon && styles['field--with-leading'],
                multiline && styles['field--multiline']
            )}
            style={style}
        >
            <div className={styles.field__box} onClick={readOnly && !disabled ? onClick : undefined}>
                {leadingIcon && <span className={styles.field__leading}><Icon name={leadingIcon} /></span>}
                {multiline ? (
                    <textarea
                        {...sharedProps}
                        className={cx(styles.field__input, styles['field__input--multiline'])}
                        onChange={(event) => onChange?.(event.target.value)}
                        rows={4}
                    />
                ) : (
                    <input
                        {...sharedProps}
                        autoComplete={autoComplete}
                        className={cx(styles.field__input, readOnly && styles['field__input--readonly'])}
                        enterKeyHint={enterKeyHint}
                        inputMode={inputMode}
                        onChange={(event) => onChange?.(event.target.value)}
                        onKeyDown={(event) => {
                            if (event.key === 'Enter' && onEnter && !event.nativeEvent.isComposing) {
                                event.preventDefault();
                                onEnter();
                            }
                        }}
                        type={type}
                    />
                )}
                {suffix && <span className={styles.field__suffix}>{suffix}</span>}
                {trailing && <span className={styles.field__trailing}>{trailing}</span>}
                {label && <label className={styles.field__label} htmlFor={id}>{label}</label>}
            </div>
            {supportingText && <div className={styles.field__supporting} id={`${id}-supporting`}>{supportingText}</div>}
        </div>
    );
}

// ---------- 칩·토글 버튼 ----------

export function ChipRow({ children, style }: { children: ReactNode; style?: CSSProperties }) {
    return <div className={styles['chip-row']} style={style}>{children}</div>;
}

type FilterChipProps = {
    label: string;
    selected: boolean;
    onClick: () => void;
    leadingIcon?: IconName;
    // 시트를 여는 칩은 뒤에 ▾를 단다. 눌림 상태 대신 "고른 값이 있다"는 뜻으로 selected를 쓴다
    trailingIcon?: IconName;
    ariaLabel?: string;
};

export function FilterChip({ label, selected, onClick, leadingIcon, trailingIcon, ariaLabel }: FilterChipProps) {
    return (
        <button
            aria-label={ariaLabel}
            aria-pressed={selected}
            className={cx(
                styles.chip,
                selected && styles['chip--selected'],
                leadingIcon && styles['chip--with-icon'],
                trailingIcon && styles['chip--with-trailing-icon'],
                'type-label-large'
            )}
            onClick={onClick}
            type="button"
        >
            {leadingIcon && <Icon name={leadingIcon} size={18} />}
            {label}
            {trailingIcon && <Icon name={trailingIcon} size={18} />}
        </button>
    );
}

type ToggleButtonGroupProps<T> = {
    options: readonly T[];
    selected: T | null;
    labelOf: (option: T) => string;
    onSelect: (option: T) => void;
    fill?: boolean;
    style?: CSSProperties;
    ariaLabel: string;
};

/** 이어 붙은 토글 버튼. 양 끝 버튼만 바깥쪽이 둥글고, 고른 버튼은 알약 모양이 된다. */
export function ToggleButtonGroup<T>({ options, selected, labelOf, onSelect, fill = false, style, ariaLabel }: ToggleButtonGroupProps<T>) {
    return (
        <div aria-label={ariaLabel} className={cx(styles['toggle-group'], fill && styles['toggle-group--fill'])} role="group" style={style}>
            {options.map((option, index) => {
                const isChecked = option === selected;
                return (
                    <button
                        aria-pressed={isChecked}
                        className={cx(
                            styles.toggle,
                            index === 0 && styles['toggle--leading'],
                            index === options.length - 1 && styles['toggle--trailing'],
                            isChecked && styles['toggle--checked'],
                            'type-label-large'
                        )}
                        key={labelOf(option)}
                        onClick={() => onSelect(option)}
                        type="button"
                    >
                        {labelOf(option)}
                    </button>
                );
            })}
        </div>
    );
}

// ---------- 다이얼로그·시트 ----------

type AlertDialogProps = {
    title: string;
    text: string;
    confirmLabel: string;
    dismissLabel: string;
    onConfirm: () => void;
    onDismiss: () => void;
    confirmDisabled?: boolean;
};

export function AlertDialog({ title, text, confirmLabel, dismissLabel, onConfirm, onDismiss, confirmDisabled }: AlertDialogProps) {
    const titleId = useId();
    useEscape(onDismiss);
    return createPortal(
        <>
            <div className={styles.scrim} />
            <div className={styles['dialog-layer']} onClick={onDismiss}>
                <div aria-labelledby={titleId} aria-modal className={styles.dialog} onClick={(event) => event.stopPropagation()} role="alertdialog">
                    <h2 className={styles.dialog__title} id={titleId}>{title}</h2>
                    <p className={cx(styles.dialog__text, 'type-body-medium')}>{text}</p>
                    <div className={styles.dialog__actions}>
                        <Button onClick={onDismiss} variant="text">{dismissLabel}</Button>
                        <Button disabled={confirmDisabled} onClick={onConfirm} variant="text">{confirmLabel}</Button>
                    </div>
                </div>
            </div>
        </>,
        document.body
    );
}

type BottomSheetProps = {
    onDismiss: () => void;
    children: ReactNode;
    tall?: boolean;
    label: string;
};

/** 화면 아래에서 올라오는 시트. 바깥을 누르거나 Esc를 누르면 닫힌다. */
export function BottomSheet({ onDismiss, children, tall = false, label }: BottomSheetProps) {
    useEscape(onDismiss);
    useEffect(() => {
        // 시트가 떠 있는 동안 뒤 화면이 스크롤되지 않게 한다
        const previous = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            document.body.style.overflow = previous;
        };
    }, []);
    return createPortal(
        <>
            <div className={styles.scrim} onClick={onDismiss} />
            <div aria-label={label} aria-modal className={cx(styles['fixed-column'], styles.sheet, tall && styles['sheet--tall'])} role="dialog">
                <div aria-hidden className={styles.sheet__handle} />
                <div className={styles.sheet__body}>{children}</div>
            </div>
        </>,
        document.body
    );
}

function useEscape(onEscape: () => void) {
    const latest = useRef(onEscape);
    latest.current = onEscape;
    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') latest.current();
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, []);
}

// ---------- 당겨서 새로고침 ----------

const PULL_TRIGGER_DISTANCE = 72;
const PULL_MAX_DISTANCE = 120;

type PullToRefreshProps = {
    isRefreshing: boolean;
    onRefresh: () => void;
    enabled?: boolean;
    children: ReactNode;
};

/** 목록 맨 위에서 아래로 당기면 새로고침한다. */
export function PullToRefresh({ isRefreshing, onRefresh, enabled = true, children }: PullToRefreshProps) {
    const [pullDistance, setPullDistance] = useState(0);
    const startY = useRef<number | null>(null);
    const distanceRef = useRef(0);
    const containerRef = useRef<HTMLDivElement | null>(null);
    const latestRefresh = useRef(onRefresh);
    latestRefresh.current = onRefresh;

    useEffect(() => {
        const container = containerRef.current;
        if (!container || !enabled) return;

        const onTouchStart = (event: TouchEvent) => {
            startY.current = window.scrollY <= 0 ? event.touches[0].clientY : null;
        };
        const onTouchMove = (event: TouchEvent) => {
            if (startY.current === null) return;
            const delta = event.touches[0].clientY - startY.current;
            if (delta <= 0 || window.scrollY > 0) {
                distanceRef.current = 0;
                setPullDistance(0);
                return;
            }
            event.preventDefault();
            // 당길수록 덜 움직여 끝에 가까워지는 느낌을 준다
            const distance = Math.min(PULL_MAX_DISTANCE, delta * 0.5);
            distanceRef.current = distance;
            setPullDistance(distance);
        };
        const onTouchEnd = () => {
            if (distanceRef.current >= PULL_TRIGGER_DISTANCE) latestRefresh.current();
            startY.current = null;
            distanceRef.current = 0;
            setPullDistance(0);
        };

        container.addEventListener('touchstart', onTouchStart, { passive: true });
        container.addEventListener('touchmove', onTouchMove, { passive: false });
        container.addEventListener('touchend', onTouchEnd);
        container.addEventListener('touchcancel', onTouchEnd);
        return () => {
            container.removeEventListener('touchstart', onTouchStart);
            container.removeEventListener('touchmove', onTouchMove);
            container.removeEventListener('touchend', onTouchEnd);
            container.removeEventListener('touchcancel', onTouchEnd);
        };
    }, [enabled]);

    const isVisible = isRefreshing || pullDistance > 0;
    const offset = isRefreshing ? 16 : pullDistance - 48;
    const progress = Math.min(1, pullDistance / PULL_TRIGGER_DISTANCE);
    return (
        <div ref={containerRef} style={{ position: 'relative', display: 'flex', flex: 1, flexDirection: 'column' }}>
            {isVisible && (
                <div
                    aria-hidden
                    className={cx(styles['pull-indicator'], isRefreshing && styles['pull-indicator--refreshing'])}
                    style={{ transform: `translateY(${offset}px)`, opacity: isRefreshing ? 1 : progress }}
                >
                    <span
                        className={styles['pull-indicator__shape']}
                        style={{ ...shapeStyle('cookie9'), transform: isRefreshing ? undefined : `rotate(${progress * 270}deg)` }}
                    />
                </div>
            )}
            {children}
        </div>
    );
}

// ---------- 진행 표시 ----------

/** 진행한 구간은 물결선으로, 남은 구간은 직선으로 그린다. 물결은 진행 방향으로 계속 흐른다. */
export function WavyProgress({ progress }: { progress: number }) {
    const width = 400;
    const height = 14;
    const middle = height / 2;
    const progressWidth = Math.max(0, Math.min(1, progress)) * width;
    const wavelength = 24;
    const amplitude = 3;
    // 물결 한 파장을 이만큼 동안 흘려 보낸다
    const periodMillis = 1000;
    const waveRef = useRef<SVGPathElement | null>(null);
    const wavePath = (phase: number) => {
        let wave = `M 0 ${middle}`;
        for (let x = 0; x <= progressWidth; x += 2) {
            wave += ` L ${x} ${middle + amplitude * Math.sin((x / wavelength - phase) * Math.PI * 2)}`;
        }
        return wave;
    };
    useEffect(() => {
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
        let frame = requestAnimationFrame(function draw(time) {
            waveRef.current?.setAttribute('d', wavePath(time / periodMillis));
            frame = requestAnimationFrame(draw);
        });
        return () => cancelAnimationFrame(frame);
    });
    const gap = 8;
    return (
        <svg
            aria-valuemax={100}
            aria-valuemin={0}
            aria-valuenow={Math.round(progress * 100)}
            className={styles['wavy-progress']}
            preserveAspectRatio="none"
            role="progressbar"
            viewBox={`0 0 ${width} ${height}`}
        >
            {progressWidth + gap < width && (
                <line
                    stroke="var(--secondary-container)"
                    strokeLinecap="round"
                    strokeWidth={4}
                    x1={Math.min(width, progressWidth + gap)}
                    x2={width - 2}
                    y1={middle}
                    y2={middle}
                />
            )}
            <path d={wavePath(0)} fill="none" ref={waveRef} stroke="var(--primary)" strokeLinecap="round" strokeWidth={4} vectorEffect="non-scaling-stroke" />
        </svg>
    );
}
