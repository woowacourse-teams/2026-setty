import { useNavigate } from 'react-router';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { favoriteRepository } from '../../api/repositories';
import { categoryLabel, formatWon, type FurnitureSummary } from '../../model/furniture';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    BackIconButton,
    CenterFill,
    ErrorContent,
    FavoriteCount,
    FlexibleTopAppBar,
    FurnitureThumbnail,
    IconButton,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SegmentedList,
    SegmentedListItem,
    SettyMessage,
    TrailingColumn
} from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';

export function FavoritesScreen() {
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, isRefreshing, load, refresh, setData } = useLoad(favoriteRepository.getFavorites);

    /** 목록에서 먼저 빼고 실패하면 되돌린다. */
    const remove = async (furniture: FurnitureSummary, index: number) => {
        setData((furnitures) => furnitures.filter((item) => item.id !== furniture.id));
        try {
            await favoriteRepository.remove(furniture.id);
        } catch (error) {
            setData((furnitures) => [...furnitures.slice(0, index), furniture, ...furnitures.slice(index)]);
            if (isUnauthorized(error)) {
                navigateToLogin();
            } else {
                showSnackbar(toUserMessage(error));
            }
        }
    };

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="찜한 가구" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={screenStyles['list-padding']}>
                        {state.data.length === 0 && (
                            <CenterFill>
                                <SettyMessage description="마음에 드는 가구의 하트를 눌러 모아보세요." title="아직 찜한 가구가 없어요" />
                            </CenterFill>
                        )}
                        <SegmentedList>
                            {state.data.map((furniture, index) => (
                                <SegmentedListItem
                                    count={state.data.length}
                                    headline={<span className="type-title-medium">{furniture.title}</span>}
                                    index={index}
                                    key={furniture.id}
                                    leading={<FurnitureThumbnail alt={furniture.title} imageUrl={furniture.thumbnailUrl} placeholder={categoryLabel(furniture.category)} />}
                                    onClick={() => navigate(routes.furnitureDetail(furniture.id))}
                                    overline={`${categoryLabel(furniture.category)} · ${furniture.conditionGrade}급`}
                                    supporting={(
                                        <>
                                            <div className={`${screenStyles['price-primary']} type-body-large-em`}>{formatWon(furniture.totalPrice)}</div>
                                            <div className="type-label-medium">배송비 {formatWon(furniture.deliveryFee)} 포함</div>
                                        </>
                                    )}
                                    trailing={(
                                        <TrailingColumn>
                                            <IconButton
                                                aria-pressed
                                                icon="favoriteFilled"
                                                iconStyle={{ color: 'var(--error)' }}
                                                label="찜 해제"
                                                onClick={() => void remove(furniture, index)}
                                            />
                                            <FavoriteCount count={furniture.favoriteCount} />
                                        </TrailingColumn>
                                    )}
                                />
                            ))}
                        </SegmentedList>
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}
