package setty.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import setty.common.s3.S3ObjectUploader;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("배차 물품 사진 업로드 API")
class DispatchItemImageApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private S3ObjectUploader s3ObjectUploader;

    @Test
    @DisplayName("이미지를 올리면 setty/images/items 키로 S3에 저장하고 공개 URL을 돌려준다")
    void uploadsImageUnderItemsPrefixAndReturnsPublicUrl() throws Exception {
        final MockMultipartFile image = new MockMultipartFile(
                "image",
                "item.jpg",
                "image/jpeg",
                "fake-image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/dispatch-requests/images").file(image))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.containsString(
                        ".s3.ap-northeast-2.amazonaws.com/setty/images/items/"
                )));

        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3ObjectUploader).upload(any(byte[].class), eq("image/jpeg"), keyCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("setty/images/items/").endsWith(".jpg");
    }

    @Test
    @DisplayName("이미지가 아닌 파일은 올릴 수 없다")
    void rejectsNonImageFile() throws Exception {
        final MockMultipartFile file = new MockMultipartFile(
                "image",
                "document.pdf",
                "application/pdf",
                "fake-pdf-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/dispatch-requests/images").file(file))
                .andExpect(status().isBadRequest());

        verify(s3ObjectUploader, never()).upload(any(byte[].class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("빈 파일은 올릴 수 없다")
    void rejectsEmptyFile() throws Exception {
        final MockMultipartFile file = new MockMultipartFile(
                "image",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/dispatch-requests/images").file(file))
                .andExpect(status().isBadRequest());

        verify(s3ObjectUploader, never()).upload(any(byte[].class), any(String.class), any(String.class));
    }
}
