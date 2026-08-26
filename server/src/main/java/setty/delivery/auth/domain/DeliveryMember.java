package setty.delivery.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "delivery_member")
public class DeliveryMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 20)
    private String loginId;

    /**
     * BCrypt 해시(60자)만 저장한다. 평문을 담지 않는다.
     */
    @Column(nullable = false, length = 60)
    private String password;

    @Column(name = "phone_number", nullable = false, length = 13)
    private String phoneNumber;

    @Column(name = "license_plate_number", nullable = false, length = 20)
    private String licensePlateNumber;

    @Column(name = "car_type", nullable = false, length = 30)
    private String carType;

    @Column(name = "business_registration_number", nullable = false, length = 12)
    private String businessRegistrationNumber;

    @Column(length = 36)
    private String token;

    protected DeliveryMember() {
    }

    public DeliveryMember(
            final String loginId,
            final String password,
            final String phoneNumber,
            final String licensePlateNumber,
            final String carType,
            final String businessRegistrationNumber
    ) {
        this.loginId = loginId;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.licensePlateNumber = licensePlateNumber;
        this.carType = carType;
        this.businessRegistrationNumber = businessRegistrationNumber;
    }

    public boolean matchPassword(final PasswordEncoder encoder, final String rawPassword) {
        return encoder.matches(rawPassword, this.password);
    }

    public void rotateToken(final String newToken) {
        this.token = newToken;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getLicensePlateNumber() {
        return licensePlateNumber;
    }

    public String getCarType() {
        return carType;
    }

    public String getBusinessRegistrationNumber() {
        return businessRegistrationNumber;
    }

    public String getToken() {
        return token;
    }
}
