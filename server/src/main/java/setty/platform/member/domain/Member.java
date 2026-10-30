package setty.platform.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "members")
public class Member {

    private static final String WITHDRAWN_LOGIN_ID_PREFIX = "withdrawn-";
    private static final String WITHDRAWN_PHONE_NUMBER = "000-0000-0000";
    private static final String WITHDRAWN_ADDRESS = "탈퇴한 회원";

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

    @Column(name = "dong_code", length = 10)
    private String dongCode;

    @Embedded
    private ResolvedAddress resolvedAddress;

    @Column(length = 36)
    private String token;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Member() {
    }

    public Member(
            final String loginId,
            final String password,
            final MemberRole role,
            final String phoneNumber,
            final String address,
            final String dongCode
    ) {
        this.loginId = loginId;
        this.password = password;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.dongCode = dongCode;
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

    public String getDongCode() {
        return dongCode;
    }

    public ResolvedAddress getResolvedAddress() {
        return resolvedAddress;
    }

    public String getZipCode() {
        return resolvedAddress == null ? null : resolvedAddress.getZipCode();
    }

    public String getToken() {
        return token;
    }

    public void rotateToken(final String newToken) {
        this.token = newToken;
    }

    public void clearToken() {
        this.token = null;
    }

    public void updateContact(final String phoneNumber, final String address) {
        this.phoneNumber = phoneNumber;
        this.address = address;
    }

    public void changeDong(final String dongCode) {
        this.dongCode = dongCode;
    }

    public void changeResolvedAddress(final ResolvedAddress resolvedAddress) {
        this.resolvedAddress = resolvedAddress;
    }

    /**
     * 거래 기록이 이 행을 가리키므로 행은 남기고 개인정보만 가린다.
     * 가린 아이디는 가입 아이디 형식(영문 소문자·숫자)에 없는 하이픈을 써서 새 가입과 겹치지 않는다.
     */
    public void withdraw(final String unusablePasswordHash, final Instant withdrawnAt) {
        this.loginId = WITHDRAWN_LOGIN_ID_PREFIX + id;
        this.password = unusablePasswordHash;
        this.phoneNumber = WITHDRAWN_PHONE_NUMBER;
        this.address = WITHDRAWN_ADDRESS;
        this.dongCode = null;
        this.resolvedAddress = ResolvedAddress.unmatched();
        this.token = null;
        this.withdrawnAt = withdrawnAt;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    public boolean matchPassword(final org.springframework.security.crypto.password.PasswordEncoder encoder, final String rawPassword) {
        return encoder.matches(rawPassword, this.password);
    }
}
