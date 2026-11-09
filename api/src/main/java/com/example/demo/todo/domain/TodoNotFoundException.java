package com.example.demo.todo.domain;

import com.example.demo.common.exception.NotFoundException;

/**
 * Todo 도메인 고유의 "없음" 예외.
 *
 * <p>공통 타입을 상속하므로 웹 계층은 이 클래스를 알 필요가 없다.
 * 도메인 안에 남겨 두는 이유는 호출부에서 무엇을 못 찾았는지 타입으로 드러내기 위함이다.
 */
public class TodoNotFoundException extends NotFoundException {

    public TodoNotFoundException(Long id) {
        super("Todo를 찾을 수 없습니다. id=" + id);
    }
}
