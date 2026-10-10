package setty.notification.keyword.application;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.notification.keyword.domain.KeywordSubscription;
import setty.notification.keyword.repository.KeywordSubscriptionRepository;

@Service
public class KeywordSubscriptionService {

    static final int MAXIMUM_KEYWORDS_PER_MEMBER = 10;

    private final KeywordSubscriptionRepository keywordSubscriptionRepository;

    public KeywordSubscriptionService(final KeywordSubscriptionRepository keywordSubscriptionRepository) {
        this.keywordSubscriptionRepository = keywordSubscriptionRepository;
    }

    public void subscribe(final Long memberId, final String keyword) {
        final KeywordSubscription subscription = new KeywordSubscription(memberId, keyword);
        validateKeywordLimit(memberId);
        saveIgnoringDuplicate(subscription);
    }

    @Transactional
    public void unsubscribe(final Long memberId, final Long subscriptionId) {
        final KeywordSubscription subscription = keywordSubscriptionRepository
                .findByIdAndMemberId(subscriptionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));
        keywordSubscriptionRepository.delete(subscription);
    }

    @Transactional(readOnly = true)
    public List<KeywordSubscription> findMine(final Long memberId) {
        return keywordSubscriptionRepository.findAllByMemberIdOrderByCreatedAtAscIdAsc(memberId);
    }

    private void validateKeywordLimit(final Long memberId) {
        if (keywordSubscriptionRepository.countByMemberId(memberId) >= MAXIMUM_KEYWORDS_PER_MEMBER) {
            throw new BusinessException(ErrorCode.TOO_MANY_KEYWORDS);
        }
    }

    private void saveIgnoringDuplicate(final KeywordSubscription subscription) {
        try {
            keywordSubscriptionRepository.saveAndFlush(subscription);
        } catch (final DataIntegrityViolationException alreadySubscribed) {
        }
    }
}
