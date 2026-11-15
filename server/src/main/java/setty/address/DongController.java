package setty.address;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DongController {

    private final SeoulDongs seoulDongs;

    @GetMapping("/api/dongs")
    public ResponseEntity<DongsResponse> dongs() {
        return ResponseEntity.ok(DongsResponse.from(seoulDongs.all()));
    }
}
