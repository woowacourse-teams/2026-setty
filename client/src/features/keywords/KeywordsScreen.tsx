import { useState } from 'react';
import { useNavigate } from 'react-router';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { keywordRepository } from '../../api/repositories';
import { conditionsSummary, hasConditions, KEYWORD_MAX_LENGTH, MAX_KEYWORDS, type Keyword } from '../../model/keyword';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
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
