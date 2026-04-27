package com.example.commerce.infrastructure.persistence.order;

import com.example.commerce.domain.order.Order;
import com.example.commerce.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Order save(Order order) {
        return jpaRepository.save(order);
    }
}
