package com.example.commerce.domain.settlement;

import java.util.List;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    boolean existsByOrderId(Long orderId);

    List<Settlement> findAll();
}
