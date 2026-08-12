package setty.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import setty.dispatch.exception.SellerInputAlreadySubmittedException;

@Entity
public class SellerInputSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_request_id", nullable = false, unique = true)
    private DispatchRequest dispatchRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerInputSessionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SellerInputSession() {
    }

    public SellerInputSession(final String token, final DispatchRequest dispatchRequest) {
        this.token = token;
        this.dispatchRequest = dispatchRequest;
        this.status = SellerInputSessionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void complete(final SellerInput input) {
        if (status == SellerInputSessionStatus.COMPLETED) {
            throw new SellerInputAlreadySubmittedException();
        }
        dispatchRequest.completeSellerInput(input);
        this.status = SellerInputSessionStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return status == SellerInputSessionStatus.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public DispatchRequest getDispatchRequest() {
        return dispatchRequest;
    }

    public SellerInputSessionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
