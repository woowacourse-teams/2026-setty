package setty.platform.order.domain;

import jakarta.persistence.Embeddable;

// 행정동·우편번호는 배송비를 계산한 구역이고, 주소·관리번호는 기사가 실제로 찾아갈 곳이다. 표준 값이 없으면 NULL이다.
@Embeddable
public record OrderPlace(
        String address,
        String phoneNumber,
        String dongCode,
        String zipCode,
        String managementNumber
) {
}
