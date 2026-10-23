import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { furnitureRepository } from '../../api/repositories';
import { categoryLabel, formatRegisteredDate, formatWon, type MyFurniture } from '../../model/furniture';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    AlertDialog,
    BackIconButton,
    Button,
    ErrorContent,
    ExtendedFab,
    FlexibleTopAppBar,
    FurnitureThumbnail,
    IconButton,
    LoadingContent,
    LoadingIndicator,
    PullToRefresh,
    SaleStatusBadge,
    Scaffold,
    SegmentedList,
    SegmentedListItem,
    SettyMessage
} from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';

export function MyListingsScreen() {
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, isRefreshing, load, refresh, setData } = useLoad(furnitureRepository.getMyFurniture);
    const [deleteTarget, setDeleteTarget] = useState<number | null>(null);
    const [deletingId, setDeletingId] = useState<number | null>(null);
    const [isFabExpanded, setIsFabExpanded] = useState(true);

    // 목록을 내리면 등록 버튼을 아이콘만 남기고 접는다
    useEffect(() => {
        const onScroll = () => setIsFabExpanded(window.scrollY < 120);
        window.addEventListener('scroll', onScroll, { passive: true });
        return () => window.removeEventListener('scroll', onScroll);
    }, []);

    const openCreate = () => navigate(routes.listingCreate);

    const remove = async (listingId: number) => {
        if (deletingId !== null) return;
        setDeletingId(listingId);
        try {
            await furnitureRepository.deleteListing(listingId);
            setData((furnitures) => furnitures.filter((furniture) => furniture.id !== listingId));
            showSnackbar('가구를 삭제했습니다');
        } catch (error) {
            if (isUnauthorized(error)) {
                navigateToLogin();
            } else {
                showSnackbar(toUserMessage(error));
            }
        } finally {
            setDeletingId(null);
        }
    };

    return (
        <Scaffold
            floatingActionButton={<ExtendedFab expanded={isFabExpanded} icon="add" label="가구 등록" onClick={openCreate} />}
            topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="내 판매 가구" />}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    {/* FAB에 마지막 항목이 가리지 않도록 아래 여백을 넉넉히 둔다 */}
                    <div className={`${screenStyles['list-padding']} ${screenStyles['list-padding--fab']}`}>
                        {state.data.length === 0 && (
                            <SettyMessage
                                action={<Button onClick={openCreate}>가구 등록하기</Button>}
                                description="쓰지 않는 가구를 올리고 새 주인을 찾아보세요."
                                style={{ marginTop: 48 }}
                                title="아직 등록한 가구가 없어요"
                            />
                        )}
                        <SegmentedList>
                            {state.data.map((furniture, index) => (
                                <MyFurnitureItem
                                    count={state.data.length}
                                    furniture={furniture}
                                    index={index}
                                    isDeleting={deletingId === furniture.id}
                                    key={furniture.id}
                                    onClick={() => navigate(routes.furnitureDetail(furniture.id))}
                                    onDeleteClick={() => setDeleteTarget(furniture.id)}
                                    onEditClick={() => navigate(routes.listingEdit(furniture.id))}
                                />
                            ))}
                        </SegmentedList>
                    </div>
                </PullToRefresh>
            )}
            {deleteTarget !== null && (
                <AlertDialog
                    confirmLabel="삭제"
                    dismissLabel="취소"
                    onConfirm={() => {
                        setDeleteTarget(null);
                        void remove(deleteTarget);
                    }}
                    onDismiss={() => setDeleteTarget(null)}
                    text="삭제한 가구는 목록에서 사라지고 되돌릴 수 없습니다."
                    title="가구를 삭제할까요?"
                />
            )}
        </Scaffold>
    );
}

type MyFurnitureItemProps = {
    furniture: MyFurniture;
    index: number;
    count: number;
    isDeleting: boolean;
    onClick: () => void;
    onEditClick: () => void;
    onDeleteClick: () => void;
};

function MyFurnitureItem({ furniture, index, count, isDeleting, onClick, onEditClick, onDeleteClick }: MyFurnitureItemProps) {
    return (
        <SegmentedListItem
            count={count}
            headline={<span className="type-title-medium">{furniture.title}</span>}
            index={index}
            leading={<FurnitureThumbnail alt={furniture.title} imageUrl={furniture.thumbnailUrl} placeholder={categoryLabel(furniture.category)} />}
            onClick={onClick}
            overline={`${categoryLabel(furniture.category)} · ${furniture.conditionGrade}급 · ${formatRegisteredDate(furniture.createdAt)}`}
            supporting={(
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <span className={`${screenStyles['price-primary']} type-body-large-em`}>{formatWon(furniture.price)}</span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <SaleStatusBadge saleStatus={furniture.saleStatus} />
                        {furniture.hasPurchaseRequest && (
                            <span className={`${screenStyles['tertiary-text']} type-label-medium`}>구매 신청 있음</span>
                        )}
                    </span>
                </div>
            )}
            trailing={isDeleting ? <LoadingIndicator /> : (
                <span style={{ display: 'flex', flexDirection: 'column' }}>
                    <IconButton disabled={!furniture.canUpdate} icon="edit" label="수정" onClick={onEditClick} />
                    <IconButton disabled={!furniture.canDelete} icon="delete" label="삭제" onClick={onDeleteClick} />
                </span>
            )}
        />
    );
}
