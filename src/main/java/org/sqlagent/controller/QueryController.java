package org.sqlagent.controller;

import org.springframework.web.bind.annotation.*;
import org.sqlagent.dto.QueryRequest;
import org.sqlagent.dto.QueryResponse;
import org.sqlagent.service.MockAiService;
import org.sqlagent.service.OllamaAiService;
import org.sqlagent.service.SqlService;
import org.sqlagent.util.SqlValidator;

import java.util.List;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final MockAiService mockAiService;
    private final SqlService sqlService;
    private final OllamaAiService ollamaAiService;

    public QueryController(
            MockAiService mockAiService,
            SqlService sqlService, OllamaAiService ollamaAiService) {

        this.mockAiService = mockAiService;
        this.sqlService = sqlService;
        this.ollamaAiService = ollamaAiService;
    }


    @GetMapping("/examples")
    public List<String> examples() {

        return ollamaAiService.generateExamples();
    }

    @PostMapping("/query")
    public QueryResponse query(
            @RequestBody QueryRequest request) {

        QueryResponse response =
                new QueryResponse();
        try {

            String sql =
                    ollamaAiService.generateSql(
                            request.getQuestion());
            SqlValidator.validate(sql);

            var data =
                    sqlService.execute(sql);

            String answer =
                    ollamaAiService.generateAnswer(
                            request.getQuestion(),
                            data
                    );

            response.setAnswer(answer);
            response.setSql(sql);
            response.setData(data);

        } catch (Exception e) {

            response.setAnswer("查询失败");

            response.setError(
                    e.getMessage()
            );

            e.printStackTrace();
        }

        return response;
    }
}