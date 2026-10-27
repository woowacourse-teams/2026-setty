package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.repository.ListingRepository;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@Transactional
class ListingTitleTransitionTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ListingRepository listings;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seller() {
        jdbc.update("""
                INSERT INTO members (id,login_id,password,role,phone_number,address)
                VALUES (987601,'title-transition','encoded','MEMBER','010-0000-0000','synthetic')
                """);
    }

    @Test
    void createAndUpdatePersistBothTitlesInTheSameTransaction() {
        Listing listing = listings.saveAndFlush(Listing.create(987601L, "1111053000", null,
                "  initial  ", "synthetic", 50000, ListingCategory.CHAIR, ConditionGrade.A, Dimensions.of(40, 40, 80)));
        assertTitles(listing.getId(), "initial");

        listing.update("  changed  ", "synthetic", 50000, ListingCategory.CHAIR,
                ConditionGrade.A, Dimensions.of(40, 40, 80));
        listings.flush();

        assertTitles(listing.getId(), "changed");
    }

    @Test
    void c0RowWithMissingNewTitleCanBeReadAndUpdated() {
        Listing listing = listings.saveAndFlush(Listing.create(987601L, "1111053000", null,
                "initial", "synthetic", 50000, ListingCategory.CHAIR, ConditionGrade.A, Dimensions.of(40, 40, 80)));
        jdbc.update("UPDATE listings SET title='written-by-c0', display_title=NULL WHERE id=?", listing.getId());
        entityManager.clear();

        Listing reloaded = listings.findById(listing.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("written-by-c0");
        reloaded.update("updated-by-c1", "synthetic", 50000, ListingCategory.CHAIR,
                ConditionGrade.A, Dimensions.of(40, 40, 80));
        listings.flush();

        assertTitles(listing.getId(), "updated-by-c1");
    }

    private void assertTitles(Long id, String title) {
        assertThat(jdbc.queryForMap("SELECT title, display_title FROM listings WHERE id=?", id))
                .containsEntry("title", title).containsEntry("display_title", title);
    }
}
