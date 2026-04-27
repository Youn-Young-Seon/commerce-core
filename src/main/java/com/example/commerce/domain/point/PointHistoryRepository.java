package com.example.commerce.domain.point;

import java.util.Optional;

public interface PointHistoryRepository {

    PointHistory save(PointHistory pointHistory);

    Optional<PointHistory> findByOrderIdAndType(Long orderId, PointHistoryType type);
}
