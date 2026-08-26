package setty.platform.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import setty.common.DeliveryStatus;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "driver_id")
    private Long driverId;

    protected Order() {
    }

    public Order(final Long listingId, final Long buyerId) {
        this.listingId = listingId;
        this.buyerId = buyerId;
        this.deliveryStatus = DeliveryStatus.REQUESTED;
    }

    public Long getId() {
        return id;
    }

    public Long getListingId() {
        return listingId;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public Long getDriverId() {
        return driverId;
    }
}
