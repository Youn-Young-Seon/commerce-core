package com.example.commerce.infrastructure.persistence.settlement;

import com.example.commerce.domain.settlement.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementJpaRepository extends JpaRepository<Settlement, Long> {

    boolean existsByOrderId(Long orderId);
}
