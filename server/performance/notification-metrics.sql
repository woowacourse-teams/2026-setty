-- 측정 후 dev DB에서 실행하는 읽기 전용 집계. 매물(등록 1건)마다 커밋~마지막 발송, 첫~마지막 발송 차이를 초 단위로 낸다.
SELECT l.id AS listing_id,
       l.title,
       l.created_at,
       COUNT(n.id) AS notifications,
       SUM(n.status = 'SENT') AS sent,
       SUM(n.status = 'PENDING') AS pending,
       SUM(n.status = 'FAILED') AS failed,
       MAX(n.attempts) AS max_attempts,
       ROUND(TIMESTAMPDIFF(MICROSECOND, l.created_at, MAX(n.sent_at)) / 1000000, 3) AS commit_to_last_sent_s,
       ROUND(TIMESTAMPDIFF(MICROSECOND, MIN(n.sent_at), MAX(n.sent_at)) / 1000000, 3) AS first_to_last_sent_s
FROM listings l
JOIN members m ON m.id = l.seller_id AND m.login_id = 's3-seller-001'
LEFT JOIN listing_notifications n ON n.listing_id = l.id
GROUP BY l.id, l.title, l.created_at
ORDER BY l.id;
