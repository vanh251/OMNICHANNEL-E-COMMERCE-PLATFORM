package com.example.orderinventoryservice;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class PostgresTestDatabase {
    private PostgresTestDatabase() {
    }

    public static void configure(DynamicPropertyRegistry registry) {
        String url = System.getenv("PERFORMANCE_TEST_POSTGRES_URL");
        if (url != null && !url.isBlank()) {
            // These tests use create-drop: accept only the disposable databases from the test runner.
            if (!url.matches("jdbc:postgresql://[^/]+/performance_test_[a-z0-9_]+")) {
                throw new IllegalArgumentException("A disposable performance_test_ database is required");
            }
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.datasource.username", () -> System.getenv("PERFORMANCE_TEST_POSTGRES_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("PERFORMANCE_TEST_POSTGRES_PASSWORD"));
            registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        }
    }
}
