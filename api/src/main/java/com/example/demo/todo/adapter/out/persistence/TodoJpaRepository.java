package com.example.demo.todo.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA 저장소. 어댑터 내부 구현 상세이므로 패키지 밖으로 노출하지 않는다.
 * {@code Pageable}/{@code Page} 같은 Spring Data 타입도 이 패키지 안에서만 쓰인다.
 */
interface TodoJpaRepository extends JpaRepository<TodoJpaEntity, Long> {

    List<TodoJpaEntity> findAllByOrderByIdAsc();

    Page<TodoJpaEntity> findByCompleted(boolean completed, Pageable pageable);
}
