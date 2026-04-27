package com.example.commerce.infrastructure.batch;

import com.example.commerce.domain.order.Order;
import com.example.commerce.domain.order.OrderRepository;
import com.example.commerce.domain.settlement.Settlement;
import com.example.commerce.domain.settlement.SettlementRepository;
import com.example.commerce.infrastructure.persistence.order.OrderJpaRepository;
import com.example.commerce.infrastructure.persistence.settlement.SettlementJpaRepository;
import com.example.commerce.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
class SettlementJobTest extends IntegrationTestSupport {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private Job settlementJob;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private SettlementJpaRepository settlementJpaRepository;

    @BeforeEach
    void setUp() {
        // 테스트 간 데이터 오염 방지 — 각 테스트 전 초기화
        settlementJpaRepository.deleteAllInBatch();
        orderJpaRepository.deleteAllInBatch();
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("CONFIRMED 주문에 대해 정산 레코드가 생성된다")
    void CONFIRMED_주문에_대해_정산_레코드가_생성된다() throws Exception {
        // given
        orderRepository.save(Order.confirmed(1L, 15_000));
        orderRepository.save(Order.confirmed(1L, 25_000));
        orderRepository.save(Order.confirmed(2L, 10_000));

        jobLauncherTestUtils.setJob(settlementJob);
        JobParameters params = new JobParametersBuilder()
                .addString("settledDate", LocalDate.now().toString())
                .addLong("run.id", System.currentTimeMillis())  // 매번 고유 파라미터로 재실행 허용
                .toJobParameters();

        // when
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements).hasSize(3);
        assertThat(settlements)
                .extracting(Settlement::getAmount)
                .containsExactlyInAnyOrder(15_000, 25_000, 10_000);
    }

    @Test
    @DisplayName("같은 주문에 대해 배치를 재실행해도 정산이 중복 생성되지 않는다 (멱등성)")
    void 배치_재실행시_정산이_중복_생성되지_않는다() throws Exception {
        // given
        orderRepository.save(Order.confirmed(1L, 50_000));

        jobLauncherTestUtils.setJob(settlementJob);
        String settledDate = LocalDate.now().toString();

        // when - 동일 주문에 대해 배치 2회 실행
        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("settledDate", settledDate)
                .addLong("run.id", 1L)
                .toJobParameters());

        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("settledDate", settledDate)
                .addLong("run.id", 2L)
                .toJobParameters());

        // then - 정산 레코드는 1건만 존재 (중복 없음)
        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements)
                .as("배치를 2회 실행해도 정산 레코드는 1건이어야 한다")
                .hasSize(1);
    }

    @Test
    @DisplayName("주문이 없으면 정산 레코드가 생성되지 않는다")
    void 주문이_없으면_정산_레코드가_생성되지_않는다() throws Exception {
        // given: 주문 없음
        jobLauncherTestUtils.setJob(settlementJob);

        // when
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("settledDate", LocalDate.now().toString())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters());

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementRepository.findAll()).isEmpty();
    }
}
