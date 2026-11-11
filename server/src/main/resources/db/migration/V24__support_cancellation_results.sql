-- Minimal business evidence follows order lifetime, not chat retention. No raw text or contact details.
CREATE TABLE ai_support_cancellation (
    request_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    member_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    question_turn_id BIGINT NOT NULL,
    consent_turn_id BIGINT NOT NULL,
    consent_method VARCHAR(16) NOT NULL,
    policy_version VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    reason_code VARCHAR(80) NULL,
    INDEX idx_support_cancellation_owner (member_id,order_id)
);
