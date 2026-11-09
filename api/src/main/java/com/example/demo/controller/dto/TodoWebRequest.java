package com.example.demo.controller.dto;

import com.example.demo.usecase.dto.AddTodoAppRequest;
import com.example.demo.usecase.dto.UpdateTodoAppRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Todo HTTP 요청 본문.
 *
 * <p>컨트롤러가 @RequestBody로 받는 타입은 항상 {@code ~WebRequest} 로 끝난다.
 * 이 타입은 웹 계층 밖으로 나가지 않고 {@code ~AppRequest} 로 변환해 유스케이스에 넘긴다.
 * Bean Validation은 입구에서 형식을 거르는 1차 방어선이고,
 * 비즈니스 불변식은 도메인 모델이 다시 검사한다.
 */
@Schema(description = "할 일 생성/수정 요청")
public record TodoWebRequest(

        @Schema(description = "제목", example = "우유 사기", maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "title은 필수입니다.")
        @Size(max = 200, message = "title은 200자를 넘을 수 없습니다.")
        String title,

        @Schema(description = "설명", example = "2L 저지방", maxLength = 2000)
        @Size(max = 2000, message = "description은 2000자를 넘을 수 없습니다.")
        String description,

        @Schema(description = "완료 여부. 생략하면 false", example = "false")
        Boolean completed
) {
    private boolean completedOrFalse() {
        return completed != null && completed;
    }

    public AddTodoAppRequest toAddAppRequest() {
        return new AddTodoAppRequest(title, description, completedOrFalse());
    }

    public UpdateTodoAppRequest toUpdateAppRequest(Long id) {
        return new UpdateTodoAppRequest(id, title, description, completedOrFalse());
    }
}
