package com.example.demo.usecase.dto;

/**
 * 수정 유스케이스 입력.
 */
public record UpdateTodoAppRequest(
        Long id,
        String title,
        String description,
        boolean completed
) {
}
