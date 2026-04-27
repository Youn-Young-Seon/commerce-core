package com.example.commerce.application.service;

import com.example.commerce.domain.stock.Stock;
import com.example.commerce.domain.stock.StockRepository;
import com.example.commerce.domain.stock.exception.StockNotFoundException;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public void decrease(Long productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));

        // 문제: 동시 요청 시 race condition 발생 가능
        // 트랜잭션 A, B가 동시에 같은 재고를 읽고 각자 차감하면 갱신 유실 발생
        stock.decrease(quantity);
        stockRepository.save(stock);
    }
}
