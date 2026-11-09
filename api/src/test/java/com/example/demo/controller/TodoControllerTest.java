package com.example.demo.controller;

import com.example.demo.controller.dto.TodoWebRequest;
import com.example.demo.todo.application.port.out.TodoRepositoryPort;
import com.example.demo.todo.domain.Todo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 → 유스케이스 → 구현체 → 도메인 서비스 → 영속성 어댑터 전 구간을 거치는 통합 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TodoRepositoryPort repository;

    @BeforeEach
    void resetStore() {
        repository.findAll().forEach(todo -> repository.deleteById(todo.getId()));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** 생성 후 발급된 id를 돌려준다. IDENTITY 채번이라 id 값을 미리 가정하지 않는다. */
    private long createTodo(TodoWebRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("생성하면 201과 Location 헤더를 반환하고 저장소에 남는다")
    void create() throws Exception {
        String body = mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TodoWebRequest("우유 사기", "2L 저지방", null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/todos/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("우유 사기"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(body).get("id").asLong();
        assertThat(repository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("title이 비면 400과 필드 오류를 반환하고 저장되지 않는다")
    void createRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TodoWebRequest("   ", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.title").exists());

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("전체 조회와 completed 필터가 동작한다")
    void listAndFilter() throws Exception {
        createTodo(new TodoWebRequest("할 일 A", null, false));
        createTodo(new TodoWebRequest("할 일 B", null, true));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/todos").param("completed", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("할 일 B"));

        mockMvc.perform(get("/api/todos").param("completed", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("할 일 A"));
    }

    @Test
    @DisplayName("page/size 로 페이지를 나눠 받는다")
    void paginates() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createTodo(new TodoWebRequest("할 일 " + i, null, false));
        }

        mockMvc.perform(get("/api/todos").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("할 일 1"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/todos").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("할 일 5"))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("page/size 가 범위를 벗어나면 400")
    void rejectsInvalidPaging() throws Exception {
        mockMvc.perform(get("/api/todos").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page는 0 이상이어야 합니다."));

        mockMvc.perform(get("/api/todos").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size는 1 이상이어야 합니다."));

        mockMvc.perform(get("/api/todos").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size는 100 이하여야 합니다."));
    }

    @Test
    @DisplayName("단건 조회 후 수정하면 변경된 값이 반영되고 createdAt은 보존된다")
    void getAndUpdate() throws Exception {
        long id = createTodo(new TodoWebRequest("원래 제목", "설명", false));
        Todo before = repository.findById(id).orElseThrow();

        mockMvc.perform(get("/api/todos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("원래 제목"));

        mockMvc.perform(put("/api/todos/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TodoWebRequest("바뀐 제목", "새 설명", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바뀐 제목"))
                .andExpect(jsonPath("$.completed").value(true));

        Todo after = repository.findById(id).orElseThrow();
        assertThat(after.getTitle()).isEqualTo("바뀐 제목");
        assertThat(after.isCompleted()).isTrue();
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isAfterOrEqualTo(before.getUpdatedAt());
    }

    @Test
    @DisplayName("삭제하면 204, 이후 조회는 404")
    void deleteThenNotFound() throws Exception {
        long id = createTodo(new TodoWebRequest("지울 항목", null, null));

        mockMvc.perform(delete("/api/todos/" + id))
                .andExpect(status().isNoContent());

        assertThat(repository.findById(id)).isEmpty();

        mockMvc.perform(get("/api/todos/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("없는 id를 수정/삭제하면 404")
    void updateAndDeleteMissing() throws Exception {
        mockMvc.perform(put("/api/todos/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TodoWebRequest("제목", null, null))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/todos/999999"))
                .andExpect(status().isNotFound());
    }
}
