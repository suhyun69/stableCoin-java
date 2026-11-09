package com.example.demo.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 예외 처리 검증.
 *
 * <p>도메인 예외가 공통 타입을 통해 HTTP로 번역되는지, 그리고 프레임워크가 던지는
 * 요청 오류들이 일관된 본문으로 돌아오는지를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("도메인의 NotFound 계열 예외는 404로 번역된다")
    void notFoundIsTranslated() throws Exception {
        mockMvc.perform(get("/api/todos/987654321"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("깨진 JSON 본문은 500이 아니라 400으로 돌아온다")
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."));
    }

    @Test
    @DisplayName("경로 변수 타입이 맞지 않으면 400으로 돌아온다")
    void typeMismatchIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/todos/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("'id' 값이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("검증 실패 응답에는 fieldErrors가 포함된다")
    void validationFailureCarriesFieldErrors() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    @DisplayName("정상 응답에는 fieldErrors가 직렬화되지 않는다")
    void errorResponseOmitsNullFields() throws Exception {
        mockMvc.perform(get("/api/todos/987654321"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
}
