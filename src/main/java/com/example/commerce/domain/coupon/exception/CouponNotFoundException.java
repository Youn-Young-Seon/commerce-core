package com.example.commerce.domain.coupon.exception;

import com.example.commerce.domain.common.exception.BusinessException;
import com.example.commerce.domain.common.exception.ErrorCode;

public class CouponNotFoundException extends BusinessException {

    public CouponNotFoundException(Long couponId) {
        super(ErrorCode.COUPON_NOT_FOUND);
    }
}
