import { useEffect, useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { keywordRepository } from '../../api/repositories';
import { categoryLabel, keywordCategories, type FurnitureCategory } from '../../model/furniture';
import {
    CONDITION_AMOUNT_MAX,
    emptyConditions,
    KEYWORD_MAX_LENGTH,
    MAX_EXCLUDE_KEYWORDS,
    type Keyword,
    type KeywordConditions
} from '../../model/keyword';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    BackIconButton,
    BottomBar,
    Button,
    ChipRow,
    ErrorContent,
    FilterChip,
    FlexibleTopAppBar,
    LoadingContent,
    LoadingIndicator,
    Scaffold,
    SectionTitle,
    TextField
} from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';

const AMOUNT_MAX_DIGITS = 9;

type ConditionsForm = {
    category: FurnitureCategory | null;
    minPrice: string;
    maxPrice: string;
    maxDeliveryFee: string;
    maxTotalPrice: string;
    excludeKeywords: string[];
};

function digits(value: string): string {
    return value.replace(/\D/g, '').slice(0, AMOUNT_MAX_DIGITS);
}

function amountText(value: number | null): string {
    return value === null ? '' : String(value);
}

function amountOf(value: string): number | null {
    return value === '' ? null : Number(value);
}

function toForm(conditions: KeywordConditions): ConditionsForm {
    return {
        category: conditions.category,
        minPrice: amountText(conditions.minPrice),
        maxPrice: amountText(conditions.maxPrice),
        maxDeliveryFee: amountText(conditions.maxDeliveryFee),
        maxTotalPrice: amountText(conditions.maxTotalPrice),
        excludeKeywords: conditions.excludeKeywords
    };
}

function toConditions(form: ConditionsForm): KeywordConditions {
    return {
        category: form.category,
        minPrice: amountOf(form.minPrice),
        maxPrice: amountOf(form.maxPrice),
        maxDeliveryFee: amountOf(form.maxDeliveryFee),
        maxTotalPrice: amountOf(form.maxTotalPrice),
        excludeKeywords: form.excludeKeywords
    };
}

function tooLarge(value: string): boolean {
    return value !== '' && Number(value) > CONDITION_AMOUNT_MAX;
}

export function KeywordConditionsScreen() {
    const { keywordId } = useParams();
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, load } = useLoad(keywordRepository.getMine);
    const keyword = state.status === 'success' ? state.data.find((item) => item.id === Number(keywordId)) ?? null : null;

    if (keyword) {
        return (
            <ConditionsEditor
                keyword={keyword}
                onBack={navigateBack}
                onSaved={() => navigate(routes.keywords, { replace: true })}
                onUnauthorized={navigateToLogin}
            />
        );
    }
    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} size="medium" title="알림 조건" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <ErrorContent isUnauthorized={false} message="존재하지 않는 키워드예요" onRequireLogin={navigateToLogin} onRetry={navigateBack} />
            )}
        </Scaffold>
    );
}

type ConditionsEditorProps = {
    keyword: Keyword;
    onBack: () => void;
    onSaved: () => void;
    onUnauthorized: () => void;
};

function ConditionsEditor({ keyword, onBack, onSaved, onUnauthorized }: ConditionsEditorProps) {
    const [form, setForm] = useState(() => toForm(keyword.conditions));
    const [excludeInput, setExcludeInput] = useState('');
    const [isSaving, setIsSaving] = useState(false);
    useEffect(() => setForm(toForm(keyword.conditions)), [keyword]);

    const update = (patch: Partial<ConditionsForm>) => setForm((current) => ({ ...current, ...patch }));

    const minPrice = amountOf(form.minPrice);
    const maxPrice = amountOf(form.maxPrice);
    const priceOrderError = minPrice !== null && maxPrice !== null && minPrice > maxPrice;
    const excludeWord = excludeInput.trim();
    const excludeError = excludeWordError(excludeWord, keyword.keyword, form.excludeKeywords);
    const canSave = !isSaving && !priceOrderError
        && ![form.minPrice, form.maxPrice, form.maxDeliveryFee, form.maxTotalPrice].some(tooLarge);

    /** 입력란의 말을 제외 키워드 칩으로 옮긴다. 엔터로도 추가되므로 엔터가 저장으로 이어지지 않는다. */
    const addExcludeWord = () => {
        if (!excludeWord || excludeError) return;
        update({ excludeKeywords: [...form.excludeKeywords, excludeWord] });
        setExcludeInput('');
    };

    const removeExcludeWord = (word: string) => {
        update({ excludeKeywords: form.excludeKeywords.filter((item) => item !== word) });
    };

    const save = async () => {
        if (!canSave) return;
        if (excludeWord) {
            showSnackbar('입력 중인 제외 키워드를 추가하거나 지워 주세요');
            return;
        }
        setIsSaving(true);
        try {
            await keywordRepository.updateConditions(keyword.id, toConditions(form));
            showSnackbar('조건을 저장했어요', { keepOnNextScreen: true });
            onSaved();
        } catch (error) {
            setIsSaving(false);
            if (isUnauthorized(error)) {
                onUnauthorized();
            } else {
                showSnackbar(toUserMessage(error));
            }
        }
    };

    return (
        <Scaffold
            bottomBar={(
                <BottomBar>
                    {isSaving ? <LoadingIndicator /> : (
                        <Button disabled={!canSave} fullWidth onClick={() => void save()} size="medium">완료</Button>
                    )}
                </BottomBar>
            )}
            topBar={<FlexibleTopAppBar navigation={<BackIconButton disabled={isSaving} onBack={onBack} />} size="medium" title="알림 조건" />}
        >
            <div className={screenStyles['list-padding']}>
                <p className="type-headline-small-em" style={{ margin: '0 0 4px' }}>{keyword.keyword}</p>
                <FormSection title="카테고리">
                    <ChipRow>
                        <FilterChip label="전체" onClick={() => update({ category: null })} selected={form.category === null} />
                        {keywordCategories.map((category) => (
                            <FilterChip
                                key={category}
                                label={categoryLabel(category)}
                                onClick={() => update({ category })}
                                selected={form.category === category}
                            />
                        ))}
                    </ChipRow>
                </FormSection>
                <FormSection description="배송비 뺀 가격" title="물품 가격">
                    <div style={{ display: 'flex', gap: 8 }}>
                        <TextField
                            enterKeyHint="next"
                            inputMode="numeric"
                            isError={priceOrderError || tooLarge(form.minPrice)}
                            label="최소"
                            onChange={(value) => update({ minPrice: digits(value) })}
                            suffix="원"
                            value={form.minPrice}
                        />
                        <TextField
                            enterKeyHint="next"
                            inputMode="numeric"
                            isError={priceOrderError || tooLarge(form.maxPrice)}
                            label="최대"
                            onChange={(value) => update({ maxPrice: digits(value) })}
                            suffix="원"
                            value={form.maxPrice}
                        />
                    </div>
                    <Hint isError={priceOrderError}>{priceOrderError ? '최소가 최대보다 커요' : '1억 원까지'}</Hint>
                </FormSection>
                <FormSection
                    description="내 주소 기준으로 계산한 배송비"
                    title="배송비"
                >
                    <TextField
                        enterKeyHint="next"
                        inputMode="numeric"
                        isError={tooLarge(form.maxDeliveryFee)}
                        label="배송비 상한"
                        onChange={(value) => update({ maxDeliveryFee: digits(value) })}
                        suffix="원"
                        value={form.maxDeliveryFee}
                    />
                    <TextField
                        enterKeyHint="next"
                        inputMode="numeric"
                        isError={tooLarge(form.maxTotalPrice)}
                        label="총액 상한 (가격 + 배송비)"
                        onChange={(value) => update({ maxTotalPrice: digits(value) })}
                        suffix="원"
                        value={form.maxTotalPrice}
                    />
                </FormSection>
                <FormSection
                    description={`제목에 있으면 알리지 않을 키워드 (최대 ${MAX_EXCLUDE_KEYWORDS}개)`}
                    title="제외 키워드"
                >
                    {form.excludeKeywords.length > 0 && (
                        <ChipRow>
                            {form.excludeKeywords.map((word) => (
                                <FilterChip key={word} label={word} leadingIcon="close" onClick={() => removeExcludeWord(word)} selected />
                            ))}
                        </ChipRow>
                    )}
                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                        <TextField
                            disabled={form.excludeKeywords.length >= MAX_EXCLUDE_KEYWORDS}
                            enterKeyHint="done"
                            isError={excludeError !== null}
                            label="제외할 키워드"
                            onChange={setExcludeInput}
                            onEnter={addExcludeWord}
                            placeholder="예: 가죽"
                            style={{ flex: 1 }}
                            supportingText={excludeError ?? `${form.excludeKeywords.length}/${MAX_EXCLUDE_KEYWORDS}개`}
                            value={excludeInput}
                        />
                        <Button disabled={!excludeWord || excludeError !== null} onClick={addExcludeWord} size="medium" style={{ marginTop: 4 }} variant="tonal">
                            추가
                        </Button>
                    </div>
                </FormSection>
                <Button
                    fullWidth
                    onClick={() => {
                        setForm(toForm(emptyConditions));
                        setExcludeInput('');
                    }}
                    style={{ marginTop: 16 }}
                    variant="text"
                >
                    조건 모두 지우기
                </Button>
            </div>
        </Scaffold>
    );
}

function excludeWordError(word: string, keyword: string, excludeKeywords: string[]): string | null {
    if (!word) return null;
    if (word.length > KEYWORD_MAX_LENGTH) return `${KEYWORD_MAX_LENGTH}자까지 입력할 수 있어요`;
    if (word.toLowerCase() === keyword.toLowerCase()) return '키워드와 같은 말은 제외할 수 없어요';
    if (excludeKeywords.some((item) => item.toLowerCase() === word.toLowerCase())) return '이미 추가한 말이에요';
    if (excludeKeywords.length >= MAX_EXCLUDE_KEYWORDS) return `${MAX_EXCLUDE_KEYWORDS}개까지만 걸 수 있어요`;
    return null;
}

function FormSection({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
    return (
        <section className={screenStyles['form-section']} style={{ paddingTop: 20 }}>
            <SectionTitle>{title}</SectionTitle>
            {description && <Hint>{description}</Hint>}
            {children}
        </section>
    );
}

function Hint({ children, isError = false }: { children: ReactNode; isError?: boolean }) {
    return (
        <p className="type-label-medium" style={{ margin: 0, whiteSpace: 'pre-line', color: isError ? 'var(--error)' : 'var(--on-surface-variant)' }}>{children}</p>
    );
}
