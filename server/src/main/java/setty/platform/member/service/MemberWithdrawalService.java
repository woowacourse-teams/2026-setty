package setty.platform.member.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.MemberWithdrawn;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.favorite.repository.FavoriteRepository;
import setty.platform.listing.application.ListingService;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
import setty.platform.order.domain.OrderStatus;
import setty.platform.order.repository.OrderRepository;

@Service
public class MemberWithdrawalService {

    private static final List<OrderStatus> ACTIVE_TRADE_STATUSES =
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.CANCEL_PENDING);

    private final MemberRepository memberRepository;
    private final ListingService listingService;
    private final OrderRepository orderRepository;
    private final FavoriteRepository favoriteRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberWithdrawalService(
            final MemberRepository memberRepository,
            final ListingService listingService,
            final OrderRepository orderRepository,
            final FavoriteRepository favoriteRepository,
            final ApplicationEventPublisher eventPublisher
    ) {
        this.memberRepository = memberRepository;
        this.listingService = listingService;
        this.orderRepository = orderRepository;
        this.favoriteRepository = favoriteRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void withdraw(final Long memberId) {
        final Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        final List<Long> listingIds = listingService.lockActiveListingIdsOf(memberId);
        validateNoActiveTrade(memberId, listingIds);

        listingService.deleteDeletableListings(listingIds);
        favoriteRepository.deleteAllByMemberId(memberId);
        member.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()), Instant.now());
        eventPublisher.publishEvent(new MemberWithdrawn(memberId));
    }

    private void validateNoActiveTrade(final Long memberId, final List<Long> listingIds) {
        final boolean buying = orderRepository.existsByBuyerIdAndOrderStatusIn(memberId, ACTIVE_TRADE_STATUSES);
        final boolean selling = !listingIds.isEmpty()
                && orderRepository.existsByListingIdInAndOrderStatusIn(listingIds, ACTIVE_TRADE_STATUSES);
        if (buying || selling) {
            throw new BusinessException(ErrorCode.MEMBER_HAS_ACTIVE_TRADE);
        }
    }
}
