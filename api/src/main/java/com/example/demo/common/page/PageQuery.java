package com.example.demo.common.page;

import com.example.demo.common.exception.InvalidInputException;

/**
 * 페이지 요청. 프레임워크 중립 타입이라 도메인/유스케이스 계층이 그대로 쓸 수 있다.
 *
 * <p>Spring Data 의 {@code Pageable} 을 쓰지 않는 이유는 그것이
 * {@code org.springframework.data} 패키지이기 때문이다. 도메인 계층이 영속성 기술을
 * 알게 되므로 {@code ArchitectureTest.도메인서비스는_JPA를_모른다} 에 걸린다.
 * 변환은 영속성 어댑터가 한다.
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new InvalidInputException("page는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new InvalidInputException("size는 1 이상이어야 합니다.");
        }
        if (size > MAX_SIZE) {
            throw new InvalidInputException("size는 " + MAX_SIZE + " 이하여야 합니다.");
        }
    }

    /** 생략된 값은 기본값으로 채운다. */
    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(
                page == null ? 0 : page,
                size == null ? DEFAULT_SIZE : size
        );
    }

    public static PageQuery first() {
        return of(null, null);
    }
}
