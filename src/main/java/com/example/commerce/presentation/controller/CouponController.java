package com.example.commerce.presentation.controller;

import com.example.commerce.application.service.CouponService;
import com.example.commerce.presentation.dto.request.CouponCreateRequest;
import com.example.commerce.presentation.dto.request.CouponIssueRequest;
import com.example.commerce.presentation.dto.response.CouponResponse;
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
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@RequestBody CouponCreateRequest request) {
        return CouponResponse.from(
                couponService.create(request.name(), request.discountAmount(), request.totalQuantity())
        );
    }

    @GetMapping("/{couponId}")
    public CouponResponse get(@PathVariable Long couponId) {
        return CouponResponse.from(couponService.getById(couponId));
    }

    @PostMapping("/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public void issue(@PathVariable Long couponId, @RequestBody CouponIssueRequest request) {
        couponService.issue(couponId, request.memberId());
    }
}
