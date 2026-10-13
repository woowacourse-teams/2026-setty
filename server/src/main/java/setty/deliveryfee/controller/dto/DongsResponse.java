package setty.deliveryfee.controller.dto;

import java.util.List;
import setty.deliveryfee.Dong;

public record DongsResponse(List<Item> items) {

    public static DongsResponse from(final List<Dong> dongs) {
        return new DongsResponse(dongs.stream()
                .map(dong -> new Item(dong.code(), dong.name(), dong.district()))
                .toList());
    }

    public record Item(String code, String name, String district) {
    }
}
