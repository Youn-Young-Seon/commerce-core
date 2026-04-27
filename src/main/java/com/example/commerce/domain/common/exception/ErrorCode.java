package com.example.commerce.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Stock
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "재고 정보를 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "S002", "재고가 부족합니다."),
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, "S003", "요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해주세요."),

    // Coupon
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "쿠폰을 찾을 수 없습니다."),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "C002", "이미 사용된 쿠폰입니다."),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT, "C003", "쿠폰이 모두 소진되었습니다."),

    // Point
    INSUFFICIENT_POINT(HttpStatus.CONFLICT, "P001", "포인트가 부족합니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "주문을 찾을 수 없습니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "회원을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
