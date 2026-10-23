import { useCallback, useEffect, useState } from 'react';
import { toLoadError, type LoadState } from '../../api/errors';
import { dongRepository } from '../../api/repositories';
import { DongCatalog, dongDisplayName, type Dong } from '../../model/dong';
import {
    BottomSheet,
    ErrorContent,
    IconButton,
    LoadingContent,
    PlainListItem,
    TextField
} from '../../ui/components';
import { Icon } from '../../ui/Icon';
import styles from '../screens.module.css';

/** 행정동 목록을 불러온다. 화면마다 한 번 부르고, 실패하면 [reload]로 다시 받는다. */
export function useDongCatalog() {
    const [catalog, setCatalog] = useState<LoadState<DongCatalog>>({ status: 'loading' });
    const reload = useCallback(() => {
        setCatalog({ status: 'loading' });
        dongRepository.getDongs()
            .then((dongs) => setCatalog({ status: 'success', data: new DongCatalog(dongs) }))
            .catch((error: unknown) => setCatalog(toLoadError(error)));
    }, []);
    useEffect(reload, [reload]);
    return { catalog, reload };
}

type DongFieldProps = {
    catalog: LoadState<DongCatalog>;
    selectedCode: string | null;
    onDongSelected: (dong: Dong) => void;
    onRetry: () => void;
    disabled?: boolean;
};

/**
 * 고른 행정동을 보여주고, 누르면 서울 행정동 목록에서 다시 고를 수 있는 입력 칸.
 * [selectedCode]가 목록에 없으면 비어 있는 칸으로 보여준다.
 */
export function DongField({ catalog, selectedCode, onDongSelected, onRetry, disabled = false }: DongFieldProps) {
    const [isPickerVisible, setIsPickerVisible] = useState(false);
    const selectedDong = catalog.status === 'success' ? catalog.data.find(selectedCode) : null;
    return (
        <>
            <TextField
                disabled={disabled}
                label="행정동"
                onClick={() => setIsPickerVisible(true)}
                placeholder="배송비 계산에 쓰일 동네를 골라주세요"
                readOnly
                supportingText="서울에서만 배송할 수 있어요"
                trailing={<Icon name="chevronRight" />}
                value={selectedDong ? dongDisplayName(selectedDong) : ''}
            />
            {isPickerVisible && (
                <DongPickerSheet
                    catalog={catalog}
                    onDismiss={() => setIsPickerVisible(false)}
                    onDongSelected={(dong) => {
                        setIsPickerVisible(false);
                        onDongSelected(dong);
                    }}
                    onRetry={onRetry}
                />
            )}
        </>
    );
}

type DongPickerSheetProps = {
    catalog: LoadState<DongCatalog>;
    onRetry: () => void;
    onDismiss: () => void;
    onDongSelected: (dong: Dong) => void;
};

function DongPickerSheet({ catalog, onRetry, onDismiss, onDongSelected }: DongPickerSheetProps) {
    return (
        <BottomSheet label="행정동 선택" onDismiss={onDismiss} tall>
            <h2 className={`${styles['sheet-title']} type-title-large-em`}>행정동 선택</h2>
            {catalog.status === 'loading' && <LoadingContent />}
            {catalog.status === 'error' && (
                <ErrorContent isUnauthorized={false} message={catalog.message} onRequireLogin={() => undefined} onRetry={onRetry} />
            )}
            {catalog.status === 'success' && <DongPickerBody catalog={catalog.data} onDongSelected={onDongSelected} />}
        </BottomSheet>
    );
}

function DongPickerBody({ catalog, onDongSelected }: { catalog: DongCatalog; onDongSelected: (dong: Dong) => void }) {
    const [query, setQuery] = useState('');
    const [selectedDistrict, setSelectedDistrict] = useState<string | null>(null);
    let list;
    if (query.trim()) {
        list = <DongList dongs={catalog.search(query)} onDongSelected={onDongSelected} showDistrict />;
    } else if (selectedDistrict !== null) {
        list = (
            <>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '0 12px' }}>
                    <IconButton icon="arrowBack" label="구 목록으로" onClick={() => setSelectedDistrict(null)} />
                    <span className="type-title-medium">{selectedDistrict}</span>
                </div>
                <DongList dongs={catalog.dongsIn(selectedDistrict)} onDongSelected={onDongSelected} showDistrict={false} />
            </>
        );
    } else {
        list = catalog.districts.map((district) => (
            <PlainListItem
                headline={district}
                key={district}
                onClick={() => setSelectedDistrict(district)}
                trailing={<Icon name="chevronRight" />}
            />
        ));
    }
    return (
        <>
            <div style={{ padding: '8px 24px' }}>
                <TextField
                    enterKeyHint="search"
                    label=""
                    leadingIcon="search"
                    onChange={setQuery}
                    placeholder="동 이름으로 검색"
                    trailing={query ? <IconButton icon="close" label="검색어 지우기" onClick={() => setQuery('')} /> : undefined}
                    type="search"
                    value={query}
                />
            </div>
            <div style={{ paddingBottom: 16 }}>{list}</div>
        </>
    );
}

function DongList({ dongs, showDistrict, onDongSelected }: { dongs: Dong[]; showDistrict: boolean; onDongSelected: (dong: Dong) => void }) {
    if (dongs.length === 0) {
        return (
            <p className="type-body-medium" style={{ margin: 0, padding: '16px 24px', color: 'var(--on-surface-variant)' }}>
                찾는 동이 없어요. 서울 행정동 이름으로 검색해 주세요.
            </p>
        );
    }
    return (
        <>
            {dongs.map((dong) => (
                <PlainListItem
                    headline={dong.name}
                    key={dong.code}
                    onClick={() => onDongSelected(dong)}
                    supporting={showDistrict ? dong.district : undefined}
                />
            ))}
        </>
    );
}
