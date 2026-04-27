package com.example.commerce.presentation.dto.response;

import com.example.commerce.domain.stock.Stock;

public record StockResponse(
        Long id,
        Long productId,
        int quantity
) {
    public static StockResponse from(Stock stock) {
        return new StockResponse(stock.getId(), stock.getProductId(), stock.getQuantity());
    }
}
