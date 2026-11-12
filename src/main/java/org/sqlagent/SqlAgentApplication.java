package org.sqlagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SqlAgentApplication {

    private static final Logger log = LoggerFactory.getLogger(SqlAgentApplication.class);

    public static void main(String[] args) {

        SpringApplication.run(SqlAgentApplication.class, args);
    }
}