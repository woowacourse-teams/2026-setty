CREATE TABLE ai_support_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    selected_order_id BIGINT NULL,
    INDEX idx_ai_support_member (member_id, last_activity_at, id),
    INDEX idx_ai_support_expiry (expires_at)
);
CREATE TABLE ai_support_turn (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    client_request_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    processing_deadline_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    answer TEXT NULL,
    cards_json JSON NULL,
    error_code VARCHAR(80) NULL,
    model_id VARCHAR(200) NULL,
    prompt_version VARCHAR(50) NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NULL,
    tool_summary_json JSON NULL,
    CONSTRAINT fk_ai_support_turn_conversation FOREIGN KEY (conversation_id)
        REFERENCES ai_support_conversation (id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_support_request UNIQUE (conversation_id, client_request_id),
    INDEX idx_ai_support_turns (conversation_id, id)
);
