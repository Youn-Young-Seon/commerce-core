package com.example.commerce.infrastructure.persistence.point;

import com.example.commerce.domain.point.PointHistory;
import com.example.commerce.domain.point.PointHistoryRepository;
import com.example.commerce.domain.point.PointHistoryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PointHistoryRepositoryImpl implements PointHistoryRepository {

    private final PointHistoryJpaRepository jpaRepository;

    @Override
    public PointHistory save(PointHistory pointHistory) {
        return jpaRepository.save(pointHistory);
    }

    @Override
    public Optional<PointHistory> findByOrderIdAndType(Long orderId, PointHistoryType type) {
        return jpaRepository.findByOrderIdAndType(orderId, type);
    }
}
