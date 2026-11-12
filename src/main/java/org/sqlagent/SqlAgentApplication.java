package org.sqlagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SqlAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SqlAgentApplication.class, args);
    }
}