package com.example.demo.common.page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 조회 결과. 프레임워크 중립 타입이다.
 *
 * <p>계층을 지나며 내용물 타입만 바꿔야 하는 경우가 많아 {@link #map(Function)} 을 둔다.
 * (예: {@code PageResult<Todo>} → {@code PageResult<TodoAppResponse>})
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (size < 1) ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResult<>(List.copyOf(content), page, size, totalElements, totalPages);
    }

    public static <T> PageResult<T> empty(PageQuery pageQuery) {
        return of(List.of(), pageQuery.page(), pageQuery.size(), 0);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(
                content.stream().<R>map(mapper).toList(),
                page, size, totalElements, totalPages
        );
    }

    public boolean isFirst() {
        return page == 0;
    }

    public boolean isLast() {
        return page >= totalPages - 1;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }
}
