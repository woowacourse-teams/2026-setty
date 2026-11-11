-- An old conversation has no active question: historical text/cards cannot authorize a new cancellation.
ALTER TABLE ai_support_conversation ADD COLUMN active_question_json JSON NULL;
ALTER TABLE ai_support_turn ADD COLUMN consent_source_turn_id BIGINT NULL;
CREATE INDEX idx_support_consent_receipt ON ai_support_turn(conversation_id,consent_source_turn_id);
