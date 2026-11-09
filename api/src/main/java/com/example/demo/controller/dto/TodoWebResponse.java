package com.example.demo.controller.dto;

import com.example.demo.usecase.dto.TodoAppResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Todo HTTP 응답 본문.
 *
 * <p>컨트롤러가 돌려주는 타입은 항상 {@code ~WebResponse} 로 끝난다.
 * 도메인 모델이 아니라 유스케이스의 {@code ~AppResponse} 에서 변환하므로,
 * 웹 계층은 도메인 모델을 아예 보지 않는다.
 */
@Schema(description = "할 일")
public record TodoWebResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "우유 사기") String title,
        @Schema(example = "2L 저지방") String description,
        @Schema(example = "false") boolean completed,
        @Schema(example = "2026-11-05T05:51:09.849454Z") Instant createdAt,
        @Schema(example = "2026-11-05T05:51:09.849454Z") Instant updatedAt
) {
    public static TodoWebResponse from(TodoAppResponse response) {
        return new TodoWebResponse(
                response.id(),
                response.title(),
                response.description(),
                response.completed(),
                response.createdAt(),
                response.updatedAt()
        );
    }
}
