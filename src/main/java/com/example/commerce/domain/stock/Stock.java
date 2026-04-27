package com.example.commerce.domain.stock;

import com.example.commerce.domain.stock.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    public static Stock of(Long productId, int quantity) {
        Stock stock = new Stock();
        stock.productId = productId;
        stock.quantity = quantity;
        return stock;
    }

    public void decrease(int amount) {
        if (this.quantity < amount) {
            throw new InsufficientStockException();
        }
        this.quantity -= amount;
    }
}
