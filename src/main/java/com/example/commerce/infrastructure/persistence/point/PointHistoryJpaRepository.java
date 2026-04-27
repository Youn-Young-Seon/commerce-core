package com.example.commerce.infrastructure.persistence.point;

import com.example.commerce.domain.point.PointHistory;
import com.example.commerce.domain.point.PointHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointHistoryJpaRepository extends JpaRepository<PointHistory, Long> {

    Optional<PointHistory> findByOrderIdAndType(Long orderId, PointHistoryType type);
}
