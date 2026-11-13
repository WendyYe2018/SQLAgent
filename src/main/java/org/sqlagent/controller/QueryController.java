package org.sqlagent.controller;

import org.springframework.web.bind.annotation.*;
import org.sqlagent.dto.QueryRequest;
import org.sqlagent.dto.QueryResponse;
import org.sqlagent.service.AgentExecutionLogService;
import org.sqlagent.service.OllamaAiService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final OllamaAiService ollamaAiService;
    private final AgentExecutionLogService agentExecutionLogService;

    public QueryController(
            OllamaAiService ollamaAiService,
            AgentExecutionLogService agentExecutionLogService) {
        this.ollamaAiService = ollamaAiService;
        this.agentExecutionLogService = agentExecutionLogService;
    }


    @GetMapping("/examples")
    public List<String> examples(@RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        // 推荐问题先按稳定的 Ollama 生成链路走，不再在这里触发异步刷新或写入向量库。
        return ollamaAiService.generateExamples();
    }

    @PostMapping("/query")
    public QueryResponse query(
            @RequestBody QueryRequest request) {

        QueryResponse response = new QueryResponse();
        try {
            String question = request.getQuestion();
            Map<String, Object> queryResult = ollamaAiService.queryWithRetry(question);
            String sql = (String) queryResult.getOrDefault("sql", "");
            List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");
            response.setStatus((String) queryResult.getOrDefault("status", "success"));
            response.setSql(sql);
            response.setData(data);
            response.setClarificationQuestion((String) queryResult.get("clarificationQuestion"));
            response.setLastAttemptSql((String) queryResult.get("lastAttemptSql"));
            response.setLastAttemptError((String) queryResult.get("lastAttemptError"));

            if ("needs_clarification".equals(response.getStatus())) {
                response.setAnswer(response.getClarificationQuestion());
                response.setSql(response.getLastAttemptSql() != null ? response.getLastAttemptSql() : sql);
                return response;
            }

            String answer = ollamaAiService.generateAnswer(question, data);
            response.setAnswer(answer);

        } catch (Exception e) {
            response.setAnswer("查询失败");
            response.setError(e.getMessage());
            response.setStatus("error");
            e.printStackTrace();
        }

        return response;
    }

    @GetMapping("/agent-logs")
    public List<Map<String, Object>> agentLogs() {
        return agentExecutionLogService.getLogs();
    }
}