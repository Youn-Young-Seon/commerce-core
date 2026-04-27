package com.example.commerce.infrastructure.batch;

import com.example.commerce.domain.order.Order;
import com.example.commerce.domain.order.OrderStatus;
import com.example.commerce.domain.settlement.Settlement;
import com.example.commerce.domain.settlement.SettlementRepository;
import com.example.commerce.infrastructure.persistence.order.OrderJpaRepository;
import com.example.commerce.infrastructure.persistence.settlement.SettlementJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class SettlementJobConfig {

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OrderJpaRepository orderJpaRepository;
    private final SettlementJpaRepository settlementJpaRepository;
    private final SettlementRepository settlementRepository;

    @Bean
    public Job settlementJob() {
        return new JobBuilder("settlementJob", jobRepository)
                .start(settlementStep())
                .build();
    }

    @Bean
    public Step settlementStep() {
        return new StepBuilder("settlementStep", jobRepository)
                .<Order, Settlement>chunk(CHUNK_SIZE, transactionManager)
                .reader(settlementReader())
                .processor(settlementProcessor(null))
                .writer(settlementWriter())
                .build();
    }

    @Bean
    public RepositoryItemReader<Order> settlementReader() {
        return new RepositoryItemReaderBuilder<Order>()
                .name("settlementReader")
                .repository(orderJpaRepository)
                .methodName("findByStatus")
                .arguments(List.of(OrderStatus.CONFIRMED))
                .sorts(Map.of("id", Sort.Direction.ASC))
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<Order, Settlement> settlementProcessor(
            @Value("#{jobParameters['settledDate']}") String settledDate) {
        return order -> {
            // 이미 정산된 주문은 null 반환 → Spring Batch가 해당 item skip
            // 동일 Job을 재실행해도 중복 정산이 발생하지 않는 멱등성 보장
            if (settlementJpaRepository.existsByOrderId(order.getId())) {
                return null;
            }
            LocalDate date = (settledDate != null)
                    ? LocalDate.parse(settledDate)
                    : LocalDate.now();
            return Settlement.of(order.getId(), date, order.getFinalPrice());
        };
    }

    @Bean
    public ItemWriter<Settlement> settlementWriter() {
        return chunk -> chunk.getItems().forEach(settlementRepository::save);
    }
}
