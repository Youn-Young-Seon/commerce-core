package com.example.commerce.domain.stock;

import com.example.commerce.domain.stock.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Test
    @DisplayName("재고를 정상적으로 차감한다")
    void 재고를_정상적으로_차감한다() {
        // given
        Stock stock = Stock.of(1L, 100);

        // when
        stock.decrease(30);

        // then
        assertThat(stock.getQuantity()).isEqualTo(70);
    }

    @Test
    @DisplayName("재고가 요청 수량과 정확히 같으면 차감에 성공한다")
    void 재고가_요청수량과_정확히_같으면_차감에_성공한다() {
        // given
        Stock stock = Stock.of(1L, 50);

        // when
        stock.decrease(50);

        // then
        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("재고가 부족하면 InsufficientStockException이 발생한다")
    void 재고가_부족하면_예외가_발생한다() {
        // given
        Stock stock = Stock.of(1L, 10);

        // when & then
        assertThatThrownBy(() -> stock.decrease(11))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("재고가 0일 때 차감하면 InsufficientStockException이 발생한다")
    void 재고가_0일때_차감하면_예외가_발생한다() {
        // given
        Stock stock = Stock.of(1L, 0);

        // when & then
        assertThatThrownBy(() -> stock.decrease(1))
                .isInstanceOf(InsufficientStockException.class);
    }
}
