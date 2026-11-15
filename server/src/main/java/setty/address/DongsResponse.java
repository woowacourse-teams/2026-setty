package setty.address;

import java.util.List;

public record DongsResponse(List<Item> items) {

    public static DongsResponse from(final List<Dong> dongs) {
        return new DongsResponse(dongs.stream()
                .map(dong -> new Item(dong.code(), dong.name(), dong.district()))
                .toList());
    }

    public record Item(String code, String name, String district) {
    }
}
