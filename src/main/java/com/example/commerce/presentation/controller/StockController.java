package com.example.commerce.presentation.controller;

import com.example.commerce.application.service.StockService;
import com.example.commerce.presentation.dto.request.StockCreateRequest;
import com.example.commerce.presentation.dto.request.StockDecreaseRequest;
import com.example.commerce.presentation.dto.response.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockResponse create(@RequestBody StockCreateRequest request) {
        return StockResponse.from(stockService.create(request.productId(), request.quantity()));
    }

    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable Long productId) {
        return StockResponse.from(stockService.getByProductId(productId));
    }

    @PostMapping("/{productId}/decrease")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decrease(@PathVariable Long productId, @RequestBody StockDecreaseRequest request) {
        stockService.decrease(productId, request.quantity());
    }
}
