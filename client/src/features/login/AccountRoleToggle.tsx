import { useNavigate } from 'react-router';
import type { AccountRole } from '../../navigation';
import { ToggleButtonGroup } from '../../ui/components';

const roles: readonly AccountRole[] = ['member', 'driver'];
const roleLabels: Record<AccountRole, string> = { member: '구매·판매', driver: '배송원' };

/** 로그인·회원가입에서 구매·판매 계정과 배송원 계정을 고른다. 고른 값은 주소에 남겨 새로고침해도 유지한다. */
export function AccountRoleToggle({ role, path }: { role: AccountRole; path: string }) {
    const navigate = useNavigate();
    return (
        <ToggleButtonGroup
            ariaLabel="계정 종류"
            labelOf={(option) => roleLabels[option]}
            onSelect={(option) => navigate(option === 'driver' ? `${path}?role=driver` : path, { replace: true })}
            options={roles}
            selected={role}
            style={{ alignSelf: 'flex-start' }}
        />
    );
}
