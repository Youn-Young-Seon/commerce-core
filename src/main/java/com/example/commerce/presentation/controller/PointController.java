package com.example.commerce.presentation.controller;

import com.example.commerce.application.service.PointService;
import com.example.commerce.presentation.dto.request.PointRequest;
import com.example.commerce.presentation.dto.response.PointHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @PostMapping("/use")
    @ResponseStatus(HttpStatus.CREATED)
    public PointHistoryResponse use(@RequestBody PointRequest request) {
        return PointHistoryResponse.from(
                pointService.use(request.memberId(), request.orderId(), request.amount())
        );
    }

    @PostMapping("/earn")
    @ResponseStatus(HttpStatus.CREATED)
    public PointHistoryResponse earn(@RequestBody PointRequest request) {
        return PointHistoryResponse.from(
                pointService.earn(request.memberId(), request.orderId(), request.amount())
        );
    }

    @PostMapping("/cancel/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long orderId) {
        pointService.cancel(orderId);
    }
}
