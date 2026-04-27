package com.example.commerce.domain.stock.exception;

import com.example.commerce.domain.common.exception.BusinessException;
import com.example.commerce.domain.common.exception.ErrorCode;

public class StockNotFoundException extends BusinessException {

    public StockNotFoundException(Long productId) {
        super(ErrorCode.STOCK_NOT_FOUND);
    }
}
