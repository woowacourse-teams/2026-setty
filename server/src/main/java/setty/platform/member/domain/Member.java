package setty.platform.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "members")
public class Member {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "phone_number", nullable = false, length = 13)
    private String phoneNumber;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(length = 36)
    private String token;

    protected Member() {
    }

    public Member(
            final String loginId,
            final String password,
            final MemberRole role,
            final String phoneNumber,
            final String address
    ) {
        this.loginId = loginId;
        this.password = password;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.address = address;
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

    public MemberRole getRole() {
        return role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public String getToken() {
        return token;
    }

    public void rotateToken(final String newToken) {
        this.token = newToken;
    }

    public boolean matchPassword(final org.springframework.security.crypto.password.PasswordEncoder encoder, final String rawPassword) {
        return encoder.matches(rawPassword, this.password);
    }
}
