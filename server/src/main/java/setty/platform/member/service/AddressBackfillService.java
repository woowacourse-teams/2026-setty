package setty.platform.member.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;

@Service
public class AddressBackfillService {

    private final MemberRepository memberRepository;
    private final MemberAddressResolver memberAddressResolver;

    public AddressBackfillService(
            final MemberRepository memberRepository,
            final MemberAddressResolver memberAddressResolver
    ) {
        this.memberRepository = memberRepository;
        this.memberAddressResolver = memberAddressResolver;
    }

    // REPEATABLE READ면 skip locked가 훑기만 한 해석된 회원과 표 끝 빈 구간까지 잠가 로그인·가입을 막는다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Long> backfillAfter(final long afterId, final int size) {
        final List<Member> members = memberRepository.lockUnresolvedAfter(afterId, size);
        members.forEach(memberAddressResolver::resolve);
        return members.stream().map(Member::getId).toList();
    }

    @Transactional(readOnly = true)
    public long countUnresolved() {
        return memberRepository.countUnresolved();
    }

    @Transactional(readOnly = true)
    public List<Long> findUnresolvedIdsAfter(final long afterId, final int size) {
        return memberRepository.findUnresolvedIdsAfter(afterId, size);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void backfill(final long memberId) {
        memberRepository.lockUnresolved(memberId).ifPresent(memberAddressResolver::resolve);
    }
}
