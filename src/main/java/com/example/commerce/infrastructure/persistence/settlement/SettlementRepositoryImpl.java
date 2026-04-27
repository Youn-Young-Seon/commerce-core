package com.example.commerce.infrastructure.persistence.settlement;

import com.example.commerce.domain.settlement.Settlement;
import com.example.commerce.domain.settlement.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SettlementRepositoryImpl implements SettlementRepository {

    private final SettlementJpaRepository jpaRepository;

    @Override
    public Settlement save(Settlement settlement) {
        return jpaRepository.save(settlement);
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        return jpaRepository.existsByOrderId(orderId);
    }

    @Override
    public List<Settlement> findAll() {
        return jpaRepository.findAll();
    }
}
