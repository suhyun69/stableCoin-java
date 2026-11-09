package com.example.demo.controller;

import com.example.demo.common.page.PageQuery;
import com.example.demo.common.page.PageResult;
import com.example.demo.controller.dto.PageWebResponse;
import com.example.demo.controller.dto.TodoWebRequest;
import com.example.demo.controller.dto.TodoWebResponse;
import com.example.demo.usecase.AddTodoUseCase;
import com.example.demo.usecase.DeleteTodoUseCase;
import com.example.demo.usecase.ReadTodoUseCase;
import com.example.demo.usecase.UpdateTodoUseCase;
import com.example.demo.usecase.dto.TodoAppResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * HTTP 요청을 유스케이스 호출로 번역한다.
 * 유스케이스 인터페이스 네 개에만 의존하고 구현체나 도메인 서비스는 알지 못한다.
 *
 * <p>요청/응답 본문은 언제나 {@code ~WebRequest} / {@code ~WebResponse} 타입으로 주고받는다.
 * 변환 흐름은 WebRequest → AppRequest → 도메인 → AppResponse → WebResponse 이며,
 * 도메인 모델은 이 클래스에 등장하지 않는다.
 *
 * <p>OpenAPI 애너테이션은 웹 계층에만 붙인다. 유스케이스/도메인은 문서화 도구도 모른다.
 */
@Tag(name = "Todo", description = "할 일 관리 API")
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final AddTodoUseCase addTodoUseCase;
    private final ReadTodoUseCase readTodoUseCase;
    private final UpdateTodoUseCase updateTodoUseCase;
    private final DeleteTodoUseCase deleteTodoUseCase;

    public TodoController(AddTodoUseCase addTodoUseCase,
                          ReadTodoUseCase readTodoUseCase,
                          UpdateTodoUseCase updateTodoUseCase,
                          DeleteTodoUseCase deleteTodoUseCase) {
        this.addTodoUseCase = addTodoUseCase;
        this.readTodoUseCase = readTodoUseCase;
        this.updateTodoUseCase = updateTodoUseCase;
        this.deleteTodoUseCase = deleteTodoUseCase;
    }

    @Operation(
            summary = "할 일 목록 조회",
            description = "페이지 단위로 조회한다. completed 로 완료 여부를 거를 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "page/size 값이 범위를 벗어남",
                    content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class)))
    })
    @GetMapping
    public PageWebResponse<TodoWebResponse> list(
            @Parameter(description = "완료 여부 필터. 생략하면 전체")
            @RequestParam(required = false) Boolean completed,

            @Parameter(description = "페이지 번호(0부터)", example = "0")
            @RequestParam(required = false) Integer page,

            @Parameter(description = "페이지 크기(1~100, 기본 20)", example = "20")
            @RequestParam(required = false) Integer size
    ) {
        PageResult<TodoAppResponse> result =
                readTodoUseCase.readAll(completed, PageQuery.of(page, size));
        return PageWebResponse.from(result, TodoWebResponse::from);
    }

    @Operation(summary = "할 일 단건 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 id 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class)))
    })
    @GetMapping("/{id}")
    public TodoWebResponse get(@PathVariable Long id) {
        return TodoWebResponse.from(readTodoUseCase.readById(id));
    }

    @Operation(summary = "할 일 등록", description = "생성된 리소스의 위치를 Location 헤더로 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성됨"),
            @ApiResponse(responseCode = "400", description = "검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class)))
    })
    @PostMapping
    public ResponseEntity<TodoWebResponse> create(@Valid @RequestBody TodoWebRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        TodoWebResponse created = TodoWebResponse.from(addTodoUseCase.add(request.toAddAppRequest()));
        URI location = uriBuilder.path("/api/todos/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "할 일 수정", description = "전체 교체. completed 를 생략하면 false 로 덮어쓴다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정됨"),
            @ApiResponse(responseCode = "400", description = "검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
            @ApiResponse(responseCode = "404", description = "해당 id 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class)))
    })
    @PutMapping("/{id}")
    public TodoWebResponse update(@PathVariable Long id, @Valid @RequestBody TodoWebRequest request) {
        return TodoWebResponse.from(updateTodoUseCase.update(request.toUpdateAppRequest(id)));
    }

    @Operation(summary = "할 일 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제됨"),
            @ApiResponse(responseCode = "404", description = "해당 id 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteTodoUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 에러 응답 스키마 노출용. 실제 직렬화는 {@code common.web.ErrorResponse} 가 하지만,
     * 그 타입은 웹 공통 계층이라 컨트롤러가 직접 참조하지 않는다.
     */
    @Schema(name = "ErrorResponse", description = "에러 응답")
    private record ErrorResponseSchema(
            @Schema(example = "2026-11-05T05:51:09.986249Z") String timestamp,
            @Schema(example = "404") int status,
            @Schema(example = "Not Found") String error,
            @Schema(example = "Todo를 찾을 수 없습니다. id=99999") String message,
            @Schema(description = "검증 실패 시에만 포함") Object fieldErrors
    ) {
    }
}
