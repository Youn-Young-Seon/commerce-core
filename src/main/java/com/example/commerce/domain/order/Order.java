package com.example.commerce.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private int finalPrice;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    public static Order confirmed(Long memberId, int finalPrice) {
        Order order = new Order();
        order.memberId = memberId;
        order.status = OrderStatus.CONFIRMED;
        order.finalPrice = finalPrice;
        order.orderedAt = LocalDateTime.now();
        return order;
    }
}
