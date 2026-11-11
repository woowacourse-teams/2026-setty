package setty.settlement;

import java.util.List;

public record SettlementsView(long confirmedTotal, List<SettlementSummary> items) {
}
