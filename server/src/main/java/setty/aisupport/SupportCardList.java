package setty.aisupport;

import java.util.AbstractList;
import java.util.List;

/** JSON DTO boundary: normalize historical stored card objects once. */
final class SupportCardList extends AbstractList<SupportCard> {
    private final List<SupportCard> values;

    SupportCardList(List<?> values) {
        this.values = values.stream().map(SupportCardCodec::read).toList();
    }

    @Override
    public SupportCard get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
    }
}
