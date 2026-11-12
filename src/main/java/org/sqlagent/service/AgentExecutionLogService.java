package org.sqlagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentExecutionLogService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionLogService.class);

    private final List<Map<String, Object>> logs = new ArrayList<>();

    public void recordSuccess(String question, String sql, List<Map<String, Object>> data, int maxAttempts, int attemptIndex) {
        Map<String, Object> entry = new ConcurrentHashMap<>();
        entry.put("timestamp", now());
        entry.put("status", "success");
        entry.put("question", question);
        entry.put("sql", sql);
        entry.put("attemptIndex", attemptIndex);
        entry.put("maxAttempts", maxAttempts);
        entry.put("rowCount", data == null ? 0 : data.size());
        entry.put("message", "SQL executed successfully");
        synchronized (logs) {
            logs.add(entry);
        }
        log.info("[AgentExecutionLog] success question={} sql={} attempt={}/{} rows={}", question, sql, attemptIndex, maxAttempts, data == null ? 0 : data.size());
    }

    public void recordFailure(String question, String sql, String error, int attemptIndex, int maxAttempts) {
        Map<String, Object> entry = new ConcurrentHashMap<>();
        entry.put("timestamp", now());
        entry.put("status", "failed");
        entry.put("question", question);
        entry.put("sql", sql);
        entry.put("attemptIndex", attemptIndex);
        entry.put("maxAttempts", maxAttempts);
        entry.put("error", error);
        entry.put("message", "SQL generation/execution failed");
        synchronized (logs) {
            logs.add(entry);
        }
        log.warn("[AgentExecutionLog] failed question={} sql={} attempt={}/{} error={}", question, sql, attemptIndex, maxAttempts, error);
    }

    public List<Map<String, Object>> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public void clear() {
        synchronized (logs) {
            logs.clear();
        }
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
