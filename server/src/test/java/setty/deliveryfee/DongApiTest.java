package setty.deliveryfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DongApiTest extends MySqlIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 로그인_없이_서울_행정동_목록을_받는다() throws Exception {
        final String body = mockMvc.perform(get("/api/dongs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        final JsonNode items = objectMapper.readTree(body).get("items");
        assertThat(items).hasSize(427);
        assertThat(items.get(1).get("code").asString()).isEqualTo("1111053000");
        assertThat(items.get(1).get("name").asString()).isEqualTo("사직동");
        assertThat(items.get(1).get("district").asString()).isEqualTo("종로구");
    }
}
