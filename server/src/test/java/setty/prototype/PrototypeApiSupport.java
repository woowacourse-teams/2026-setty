package setty.prototype;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import setty.common.s3.S3ObjectUploader;

/**
 * 프로토타입 API 테스트가 공통으로 쓰는 가입·로그인 세션과 매물 등록을 준비한다.
 * 실제 개인정보를 쓰지 않도록 가상 휴대폰 번호와 가상 비밀번호만 사용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class PrototypeApiSupport {
    protected static final String PASSWORD = "test-password-1";
    protected static final String SELLER_PHONE_NUMBER = "010-0000-0001";
    protected static final String OTHER_SELLER_PHONE_NUMBER = "010-0000-0002";

    /**
     * 테스트가 실제 S3로 사진을 올리지 않도록 막는다.
     */
    @MockitoBean
    protected S3ObjectUploader s3ObjectUploader;

    @Autowired
    protected MockMvc mockMvc;

    /**
     * 처음 보는 번호로 로그인하면 가입까지 함께 이뤄진다.
     */
    protected MockHttpSession logIn(final String phoneNumber) throws Exception {
        final MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsPayload(phoneNumber, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected long createListing(final MockHttpSession session, final String title) throws Exception {
        return createListing(session, title, 1);
    }

    protected long createListing(final MockHttpSession session, final String title, final int imageCount)
            throws Exception {
        final MockMultipartHttpServletRequestBuilder request = multipart("/api/listings")
                .file(requestPart("""
                        {
                          "title": "%s",
                          "description": "사용감이 조금 있습니다.",
                          "pickupTimeText": "평일 오후 7시 이후",
                          "canHelpMove": true
                        }
                        """.formatted(title)));
        for (int order = 1; order <= imageCount; order++) {
            request.file(imagePart("item-" + order + ".jpg", "image/jpeg"));
        }

        final MvcResult result = mockMvc.perform(request.session(session))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(responseBodyOf(result), "$.listingId")).longValue();
    }

    protected MockMultipartFile requestPart(final String json) {
        return new MockMultipartFile(
                "request",
                "request.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)
        );
    }

    protected MockMultipartFile imagePart(final String fileName, final String contentType) {
        return new MockMultipartFile("images", fileName, contentType, "fake-image-bytes".getBytes());
    }

    protected String credentialsPayload(final String phoneNumber, final String password) {
        return """
                {
                  "phoneNumber": "%s",
                  "password": "%s"
                }
                """.formatted(phoneNumber, password);
    }

    protected String responseBodyOf(final MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
