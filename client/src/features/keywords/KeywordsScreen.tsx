import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { keywordRepository } from '../../api/repositories';
import { conditionsSummary, hasConditions, KEYWORD_MAX_LENGTH, MAX_KEYWORDS, type Keyword } from '../../model/keyword';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import { enablePush, getPushState, type PushState } from '../../push/webPush';
import {
    AlertDialog,
    BackIconButton,
    Button,
    ErrorContent,
    FlexibleTopAppBar,
    IconButton,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SegmentedList,
    SegmentedListItem,
    SettyMessage,
    TextField
} from '../../ui/components';
import { Icon } from '../../ui/Icon';
import { showSnackbar } from '../../ui/snackbar';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';

export function KeywordsScreen() {
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, isRefreshing, load, refresh, fetch, setData } = useLoad(keywordRepository.getMine);
    const [input, setInput] = useState('');
    const [isAdding, setIsAdding] = useState(false);

    const handleError = (error: unknown) => {
        if (isUnauthorized(error)) {
            navigateToLogin();
        } else {
            showSnackbar(toUserMessage(error));
        }
    };

    const add = async () => {
        const keyword = input.trim();
        if (!keyword || isAdding || state.status !== 'success') return;
        if (keyword.length > KEYWORD_MAX_LENGTH) {
            showSnackbar(`키워드는 ${KEYWORD_MAX_LENGTH}자까지 입력할 수 있어요`);
            return;
        }
        if (state.data.some((item) => item.keyword.toLowerCase() === keyword.toLowerCase())) {
            showSnackbar('이미 등록한 키워드예요');
            return;
        }
        if (state.data.length >= MAX_KEYWORDS) {
            showSnackbar(`키워드는 ${MAX_KEYWORDS}개까지 등록할 수 있어요`);
            return;
        }
        setIsAdding(true);
        try {
            await keywordRepository.subscribe(keyword);
            setInput('');
            await fetch();
        } catch (error) {
            handleError(error);
        } finally {
            setIsAdding(false);
        }
    };

    /** 목록에서 먼저 빼고 실패하면 되돌린다. */
    const remove = async (keyword: Keyword, index: number) => {
        setData((keywords) => keywords.filter((item) => item.id !== keyword.id));
        try {
            await keywordRepository.unsubscribe(keyword.id);
        } catch (error) {
            setData((keywords) => [...keywords.slice(0, index), keyword, ...keywords.slice(index)]);
            handleError(error);
        }
    };

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="키워드 알림" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={screenStyles['list-padding']}>
                        <PushToggle />
                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, marginBottom: 16 }}>
                            <TextField
                                enterKeyHint="done"
                                label="키워드"
                                onChange={setInput}
                                onEnter={() => void add()}
                                placeholder="예: 소파, 원목 책상"
                                style={{ flex: 1 }}
                                supportingText={`${state.data.length}/${MAX_KEYWORDS}개 등록`}
                                value={input}
                            />
                            <Button disabled={isAdding || !input.trim()} onClick={() => void add()} size="medium" style={{ marginTop: 4 }}>
                                등록
                            </Button>
                        </div>
                        {state.data.length === 0 && (
                            <SettyMessage
                                description="제목에 키워드가 들어간 새 매물을 알려드려요."
                                style={{ marginTop: 32 }}
                                title="아직 등록한 키워드가 없어요"
                            />
                        )}
                        <SegmentedList>
                            {state.data.map((keyword, index) => (
                                <SegmentedListItem
                                    count={state.data.length}
                                    headline={<span className="type-title-medium">{keyword.keyword}</span>}
                                    index={index}
                                    key={keyword.id}
                                    onClick={() => navigate(routes.keywordConditions(keyword.id))}
                                    supporting={(
                                        <span className="type-label-medium">
                                            {hasConditions(keyword.conditions) ? conditionsSummary(keyword.conditions) : '조건 없음'}
                                        </span>
                                    )}
                                    trailing={<IconButton icon="delete" label={`${keyword.keyword} 삭제`} onClick={() => void remove(keyword, index)} />}
                                />
                            ))}
                        </SegmentedList>
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}

/** 브라우저 알림 켜기. 설정값이 없거나 지원하지 않는 브라우저면 아무것도 보여주지 않는다. */
function PushToggle() {
    const [pushState, setPushState] = useState<PushState>('unsupported');
    const [isEnabling, setIsEnabling] = useState(false);
    const [isIosGuideVisible, setIsIosGuideVisible] = useState(false);
    const navigateToLogin = useNavigateToLogin();

    useEffect(() => {
        void getPushState().then(setPushState);
    }, []);

    const enable = async () => {
        if (pushState === 'ios-needs-install') {
            setIsIosGuideVisible(true);
            return;
        }
        setIsEnabling(true);
        try {
            const result = await enablePush();
            setPushState(result);
            if (result === 'granted') showSnackbar('브라우저 알림을 켰어요');
        } catch (error) {
            if (isUnauthorized(error)) {
                navigateToLogin();
            } else {
                showSnackbar(toUserMessage(error, '알림을 켜지 못했어요'));
            }
        } finally {
            setIsEnabling(false);
        }
    };

    if (pushState === 'unsupported') return null;
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            {pushState === 'granted' && (
                <span className="type-label-large" style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--primary)' }}>
                    <Icon name="notifications" size={18} />브라우저 알림 켜짐
                </span>
            )}
            {pushState === 'denied' && (
                <span className="type-label-medium" style={{ color: 'var(--on-surface-variant)' }}>
                    브라우저 설정에서 알림이 차단돼 있어요
                </span>
            )}
            {(pushState === 'default' || pushState === 'ios-needs-install') && (
                <Button disabled={isEnabling} onClick={() => void enable()} variant="tonal">브라우저 알림 받기</Button>
            )}
            {isIosGuideVisible && (
                <AlertDialog
                    confirmLabel="확인"
                    dismissLabel="닫기"
                    onConfirm={() => setIsIosGuideVisible(false)}
                    onDismiss={() => setIsIosGuideVisible(false)}
                    text="사파리 공유 버튼 → 홈 화면에 추가 → 추가된 세티를 열고 다시 눌러 주세요."
                    title="홈 화면에 추가한 뒤 켤 수 있어요"
                />
            )}
        </div>
    );
}
