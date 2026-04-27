package com.example.commerce.presentation.dto.request;

public record StockCreateRequest(
        Long productId,
        int quantity
) {
}
