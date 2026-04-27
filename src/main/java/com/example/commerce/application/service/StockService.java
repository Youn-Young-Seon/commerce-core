package com.example.commerce.application.service;

import com.example.commerce.domain.stock.Stock;
import com.example.commerce.domain.stock.StockRepository;
import com.example.commerce.domain.stock.exception.StockNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public Stock create(Long productId, int quantity) {
        Stock stock = Stock.of(productId, quantity);
        return stockRepository.save(stock);
    }

    @Transactional(readOnly = true)
    public Stock getByProductId(Long productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public void decrease(Long productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));

        // @Version 필드로 인해 JPA가 다음 쿼리를 실행:
        // UPDATE stock SET quantity=?, version=version+1
        // WHERE id=? AND version=?
        // version 불일치 시 0 rows affected → ObjectOptimisticLockingFailureException
        // → @Retryable이 최대 3회, 100ms 간격으로 재시도
        stock.decrease(quantity);
        stockRepository.save(stock);
    }
}
