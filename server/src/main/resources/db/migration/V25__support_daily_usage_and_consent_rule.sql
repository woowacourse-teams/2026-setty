-- Independent of chat retention: deleting a conversation cannot reset the daily allowance.
CREATE TABLE ai_support_daily_usage (
    member_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    text_turns INT NOT NULL DEFAULT 0,
    PRIMARY KEY (member_id, usage_date),
    INDEX idx_ai_support_daily_usage_date (usage_date)
);

-- Only newly accepted TEXT consent records its rule; old rows and button consent remain NULL.
ALTER TABLE ai_support_cancellation ADD COLUMN consent_rule_version VARCHAR(40) NULL;
