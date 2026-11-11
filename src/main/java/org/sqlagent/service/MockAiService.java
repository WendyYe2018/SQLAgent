package org.sqlagent.service;

import org.springframework.stereotype.Service;

@Service
public class MockAiService implements AiService{

    @Override
    public String generateSql(String question) {

        String q = question.toLowerCase();

        if (q.contains("order")) {
            return """
                SELECT COUNT(*)
                AS total_orders
                FROM orders
                """;
        }

        if (q.contains("product")) {
            return """
                SELECT TOP 10 *
                FROM products
                """;
        }

        if (q.contains("customer")) {
            return """
                SELECT COUNT(*)
                AS total_customers
                FROM customers
                """;
        }

        return """
            SELECT TOP 10 *
            FROM orders
            """;
    }
}