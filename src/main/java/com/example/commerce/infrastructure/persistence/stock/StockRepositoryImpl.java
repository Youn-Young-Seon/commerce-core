package com.example.commerce.infrastructure.persistence.stock;

import com.example.commerce.domain.stock.Stock;
import com.example.commerce.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository jpaRepository;

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        return jpaRepository.findByProductId(productId);
    }

    @Override
    public Stock save(Stock stock) {
        return jpaRepository.save(stock);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
