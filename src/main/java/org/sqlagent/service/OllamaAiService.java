package org.sqlagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.sqlagent.util.SqlCleaner;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaAiService {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    SchemaService schemaService;

    @Autowired
    PromptService promptService;

    public String generateSql(String question) throws IOException {

        String prompt =
                promptService
                        .loadPrompt(
                                "sql-generation.txt");

        prompt = prompt
                .replace(
                        "{{schema}}",
                        schemaService.getSchema().toString()
                )
                .replace(
                        "{{question}}",
                        question
                );

        log.info("生成 SQL Prompt: {}", prompt);

        try {
            return SqlCleaner.clean(
                    callOllama(prompt)
            );
        } catch (Exception e) {
            log.error("生成 SQL 失败，question={}", question, e);
            throw e;
        }
    }

    private String callOllama(String prompt) {

        Map<String, Object> request =
                new HashMap<>();

        request.put("model", "qwen2.5:3b");
        request.put("prompt", prompt);
        request.put("stream", false);

        log.info("Ollama 请求: {}", request);

        try {
            Map response =
                    restTemplate.postForObject(
                            "http://localhost:11434/api/generate",
                            request,
                            Map.class
                    );

            log.info("Ollama 原始返回: {}", response);

            return String.valueOf(
                    response.get("response")
            );
        } catch (Exception e) {
            log.error("调用 Ollama 失败，prompt={}", prompt, e);
            throw e;
        }
    }

    public String generateAnswer(
            String question,
            Object result) throws IOException {
        String prompt =
                promptService
                        .loadPrompt(
                                "answer-generation.txt");

        prompt = prompt
                .replace(
                        "{{question}}",
                        question
                )
                .replace(
                        "{{result}}",
                        result.toString()
                );

        try {
            Map<String, Object> request =
                    new HashMap<>();

            request.put("model", "qwen2.5:3b");
            request.put("prompt", prompt);
            request.put("stream", false);

            Map response =
                    restTemplate.postForObject(
                            "http://localhost:11434/api/generate",
                            request,
                            Map.class
                    );

            return String.valueOf(
                    response.get("response")
            );
        } catch (Exception e) {
            log.error("生成回答失败，question={}, result={}", question, result, e);
            throw e;
        }
    }

    @Cacheable(value = "example-questions", key = "'default'")
    public List<String> generateExamples()  {
        String schema =
                schemaService.getSchema();

        String prompt =
                promptService.loadPrompt(
                        "example-questions.txt"
                );

        prompt = prompt.replace(
                "{{schema}}",
                schema
        );

        try {
            String result =
                    callOllama(prompt);

            return Arrays.stream(
                            result.split("\n"))
                    .filter(s -> !s.isBlank())
                    .toList();
        } catch (Exception e) {
            log.error("生成推荐问题失败，schema={}", schema, e);
            throw e;
        }
    }
}