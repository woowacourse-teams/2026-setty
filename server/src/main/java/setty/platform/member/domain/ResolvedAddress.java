package setty.platform.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import setty.address.AddressResolution;
import setty.address.StandardAddress;

@Embeddable
public class ResolvedAddress {

    @Column(name = "address_mgmt_no", length = 25)
    private String managementNumber;

    @Column(name = "road_address", length = 200)
    private String roadAddress;

    @Column(name = "address_detail", length = 100)
    private String detail;

    @Column(name = "zip_code", length = 5)
    private String zipCode;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_status", length = 20)
    private AddressResolution.Status status;

    protected ResolvedAddress() {
    }

    private ResolvedAddress(final AddressResolution.Status status, final StandardAddress address) {
        this.status = status;
        if (address != null) {
            this.managementNumber = address.managementNumber();
            this.roadAddress = address.roadAddress();
            this.detail = address.detail();
            this.zipCode = address.zipCode();
            this.latitude = address.latitude();
            this.longitude = address.longitude();
        }
    }

    public static ResolvedAddress from(final AddressResolution resolution) {
        return new ResolvedAddress(resolution.status(), resolution.address());
    }

    public String getManagementNumber() {
        return managementNumber;
    }

    public String getRoadAddress() {
        return roadAddress;
    }

    public String getDetail() {
        return detail;
    }

    public String getZipCode() {
        return zipCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public AddressResolution.Status getStatus() {
        return status;
    }
}
