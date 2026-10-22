package setty.address;

import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import setty.address.AddressReferenceRepository.Row;
import setty.address.AddressText.Candidate;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressReferenceRepository repository;

    public AddressResolution resolve(final String freeText) {
        if (freeText == null || freeText.isBlank()) {
            return AddressResolution.unmatched();
        }
        final String text = AddressText.normalize(freeText);
        for (final Candidate road : AddressText.roads(text)) {
            final List<Row> rows = narrow(repository.findByRoad(road.name(), road.main(), road.sub()), road,
                    row -> row.underground() == road.special());
            if (!rows.isEmpty()) {
                return decide(rows, road);
            }
        }
        for (final Candidate jibun : AddressText.jibuns(text)) {
            final List<Row> rows = narrow(repository.findByJibun(jibun.name(), jibun.main(), jibun.sub()), jibun,
                    row -> row.mountain() == jibun.special());
            if (!rows.isEmpty()) {
                return decide(rows, jibun);
            }
        }
        return AddressResolution.unmatched();
    }

    // 구 이름을 썼으면 그 구만 남긴다. 지하·산 표기는 맞는 쪽이 있을 때만 좁힌다(대부분 생략하므로).
    private List<Row> narrow(final List<Row> rows, final Candidate candidate, final Predicate<Row> sameSpecial) {
        final List<Row> inDistrict = candidate.district() == null ? rows
                : rows.stream().filter(row -> row.district().equals(candidate.district())).toList();
        final List<Row> special = inDistrict.stream().filter(sameSpecial).toList();
        return special.isEmpty() ? inDistrict : special;
    }

    private AddressResolution decide(final List<Row> rows, final Candidate candidate) {
        if (rows.size() > 1) {
            return AddressResolution.ambiguous();
        }
        return AddressResolution.matched(rows.getFirst().toStandard(candidate.detail()));
    }
}
