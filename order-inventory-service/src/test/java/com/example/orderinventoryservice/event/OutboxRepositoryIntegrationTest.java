package com.example.orderinventoryservice.event;

import com.example.orderinventoryservice.entity.OutboxEvent;
import com.example.orderinventoryservice.PostgresTestDatabase;
import com.example.orderinventoryservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.orderinventoryservice.service.ProductServiceClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutboxRepositoryIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry);
    }
    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private ProductServiceClient productServiceClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void respectsBatchLimitAndExcludesPublishedEvents() {
        OutboxEvent first = repository.save(new OutboxEvent("order.created", "{}"));
        OutboxEvent second = repository.save(new OutboxEvent("order.created", "{}"));
        OutboxEvent third = repository.save(new OutboxEvent("order.created", "{}"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var batch = repository.findPendingBatch(2);
            assertThat(batch).extracting(OutboxEvent::getId).containsExactly(first.getId(), second.getId());
            batch.forEach(OutboxEvent::markPublished);
        });
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(repository.findPendingBatch(2)).extracting(OutboxEvent::getId)
                        .containsExactly(third.getId()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PERFORMANCE_TEST_POSTGRES_URL", matches = ".+")
    void concurrentPublisherSkipsLockedRowsAndRollbackMakesThemAvailableAgain() throws Exception {
        OutboxEvent first = repository.save(new OutboxEvent("order.created", "{}"));
        OutboxEvent second = repository.save(new OutboxEvent("order.created", "{}"));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> worker = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        assertThat(repository.findPendingBatch(1)).extracting(OutboxEvent::getId)
                                .containsExactly(first.getId());
                        locked.countDown();
                        try {
                            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        status.setRollbackOnly();
                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    assertThat(repository.findPendingBatch(2)).extracting(OutboxEvent::getId)
                            .containsExactly(second.getId()));
            release.countDown();
            worker.get(10, TimeUnit.SECONDS);
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    assertThat(repository.findPendingBatch(2)).extracting(OutboxEvent::getId)
                            .containsExactly(first.getId(), second.getId()));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
