package cloud.setty.setty.feature.dong

import cloud.setty.setty.model.Dong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DongCatalogTest {

    private val catalog = DongCatalog(
        listOf(
            Dong(code = "1111053000", name = "사직동", district = "종로구"),
            Dong(code = "1111051500", name = "청운효자동", district = "종로구"),
            Dong(code = "1114052000", name = "소공동", district = "중구"),
            Dong(code = "1168064000", name = "역삼1동", district = "강남구"),
        )
    )

    @Test
    fun `구는 가나다순으로 한 번씩 보여준다`() {
        assertEquals(listOf("강남구", "종로구", "중구"), catalog.districts)
    }

    @Test
    fun `구를 고르면 그 구의 동을 가나다순으로 보여준다`() {
        assertEquals(listOf("사직동", "청운효자동"), catalog.dongsIn("종로구").map { it.name })
    }

    @Test
    fun `동 이름 일부로 찾는다`() {
        assertEquals(listOf("역삼1동"), catalog.search("역삼").map { it.name })
    }

    @Test
    fun `구 이름과 함께 띄어 써서 찾아도 된다`() {
        assertEquals(listOf("소공동"), catalog.search("중구 소공").map { it.name })
    }

    @Test
    fun `빈 검색어는 결과가 없다`() {
        assertTrue(catalog.search("  ").isEmpty())
    }

    @Test
    fun `코드로 동을 찾고 없는 코드나 null이면 null이다`() {
        assertEquals("청운효자동", catalog.find("1111051500")?.name)
        assertNull(catalog.find("0000000000"))
        assertNull(catalog.find(null))
    }
}
