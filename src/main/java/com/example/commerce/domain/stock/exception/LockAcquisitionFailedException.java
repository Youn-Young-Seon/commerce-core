package com.example.commerce.domain.stock.exception;

import com.example.commerce.domain.common.exception.BusinessException;
import com.example.commerce.domain.common.exception.ErrorCode;

public class LockAcquisitionFailedException extends BusinessException {

    public LockAcquisitionFailedException(Long productId) {
        super(ErrorCode.LOCK_ACQUISITION_FAILED);
    }
}
