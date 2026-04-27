package com.example.commerce.domain.coupon.exception;

import com.example.commerce.domain.common.exception.BusinessException;
import com.example.commerce.domain.common.exception.ErrorCode;

public class CouponAlreadyUsedException extends BusinessException {

    public CouponAlreadyUsedException() {
        super(ErrorCode.COUPON_ALREADY_USED);
    }
}
