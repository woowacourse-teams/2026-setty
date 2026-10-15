package setty.platform.listing.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import setty.platform.listing.application.ListingView;

// 커서 없이 부르는 기존 클라이언트의 응답({"items": [...]})을 그대로 두려고 다음 커서가 없으면 필드를 뺀다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListingPageResponse(List<ListingView.Summary> items, String nextCursor) {
}
