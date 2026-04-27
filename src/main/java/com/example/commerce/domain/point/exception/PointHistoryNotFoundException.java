package com.example.commerce.domain.point.exception;

import com.example.commerce.domain.common.exception.BusinessException;
import com.example.commerce.domain.common.exception.ErrorCode;

public class PointHistoryNotFoundException extends BusinessException {

    public PointHistoryNotFoundException(Long orderId) {
        super(ErrorCode.POINT_HISTORY_NOT_FOUND);
    }
}
