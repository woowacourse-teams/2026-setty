package setty.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Embeddable
public record FurnitureInfo(
        @Column(name = "item_name", nullable = false, length = 100) String itemName,
        @Column(nullable = false, length = 50) String category
) {

    public FurnitureInfo {
        if (itemName == null || itemName.isBlank() || category == null || category.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        itemName = itemName.trim();
        category = category.trim();
    }
}
