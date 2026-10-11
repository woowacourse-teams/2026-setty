# 만료 주문의 결제 수동 확인

주문 만료가 먼저 확정된 뒤 결제 승인이 기록되면 주문은 `EXPIRED`로 남고 배송 요청은 발행되지 않는다. 자동 환불은 현재 구현 범위가 아니다.

다음 조회로 `EXPIRED` 주문의 `DONE` 결제를 확인한다. 주문 모듈의 런타임 코드는 결제 테이블을 조회하지 않는다.

```sql
SELECT o.id AS order_id, p.id AS payment_id, p.toss_order_id, p.payment_key, p.amount, p.approved_at
FROM orders o
JOIN payments p ON p.order_id = o.id
WHERE o.order_status = 'EXPIRED' AND p.status = 'DONE';
```

운영자는 조회 결과와 토스 승인 결과를 대조한 뒤 필요한 환불을 수동으로 처리한다. 애플리케이션의 자동 환불과 처리 완료 기록은 후속 구현 범위다. `PaymentCompleted` 처리 로그의 `orderId`는 확인 대상을 찾는 단서이며, 로그가 없어도 위 조회로 누락 건을 찾을 수 있다.
