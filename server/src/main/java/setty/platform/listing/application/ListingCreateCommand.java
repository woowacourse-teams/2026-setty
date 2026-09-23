package setty.platform.listing.application;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.ListingCategory;

public record ListingCreateCommand(
        String title,
        String description,
        Integer price,
        ListingCategory category,
        ConditionGrade conditionGrade,
        Dimensions dimensions,
        List<MultipartFile> images
) {
}
