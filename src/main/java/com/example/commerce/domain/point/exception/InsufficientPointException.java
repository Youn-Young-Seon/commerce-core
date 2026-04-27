package com.example.commerce.domain.point.exception;

import com.example.commerce.domain.common.exception.BusinessException;
import com.example.commerce.domain.common.exception.ErrorCode;

public class InsufficientPointException extends BusinessException {

    public InsufficientPointException() {
        super(ErrorCode.INSUFFICIENT_POINT);
    }
}
