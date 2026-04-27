package com.example.commerce.domain.coupon.exception;

import com.example.commerce.domain.common.exception.BusinessException;
import com.example.commerce.domain.common.exception.ErrorCode;

public class CouponExhaustedException extends BusinessException {

    public CouponExhaustedException(Long couponId) {
        super(ErrorCode.COUPON_EXHAUSTED);
    }
}
