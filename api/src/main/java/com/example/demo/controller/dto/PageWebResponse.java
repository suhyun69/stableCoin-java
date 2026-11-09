package com.example.demo.controller.dto;

import com.example.demo.common.page.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 응답 본문. 내용물 타입만 바꿔 가며 재사용한다.
 */
@Schema(description = "페이지 조회 결과")
public record PageWebResponse<T>(

        @Schema(description = "현재 페이지의 항목들")
        List<T> content,

        @Schema(description = "현재 페이지 번호(0부터)", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "전체 항목 수", example = "42")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "3")
        int totalPages,

        @Schema(description = "첫 페이지 여부")
        boolean first,

        @Schema(description = "마지막 페이지 여부")
        boolean last
) {
    public static <S, T> PageWebResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageWebResponse<>(
                result.content().stream().map(mapper).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
