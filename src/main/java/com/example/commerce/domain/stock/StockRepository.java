package com.example.commerce.domain.stock;

import java.util.Optional;

public interface StockRepository {

    Optional<Stock> findByProductId(Long productId);

    Stock save(Stock stock);
}
