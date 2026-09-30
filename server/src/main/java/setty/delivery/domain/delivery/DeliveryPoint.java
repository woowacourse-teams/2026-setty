package setty.delivery.domain.delivery;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.util.Objects;

/** 배송 경로의 한 지점. 주소와 그 지점의 연락처를 함께 갖는다. */
@Embeddable
public class DeliveryPoint {

    @Embedded
    private Address address;

    @Embedded
    private PhoneNumber phoneNumber;

    protected DeliveryPoint() {
    }

    private DeliveryPoint(final String address, final String phoneNumber) {
        this.address = new Address(address);
        this.phoneNumber = new PhoneNumber(phoneNumber);
    }

    // 출발지·도착지 역할은 DeliveryRoute에서의 위치로 정해진다. 이름은 생성하는 쪽의 의도를 드러내기 위한 것이다.
    public static DeliveryPoint pickup(final String address, final String phoneNumber) {
        return new DeliveryPoint(address, phoneNumber);
    }

    public static DeliveryPoint destination(final String address, final String phoneNumber) {
        return new DeliveryPoint(address, phoneNumber);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DeliveryPoint that)) {
            return false;
        }
        return Objects.equals(address, that.address) && Objects.equals(phoneNumber, that.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, phoneNumber);
    }
}
