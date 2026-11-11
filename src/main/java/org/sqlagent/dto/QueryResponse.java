package org.sqlagent.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

public class QueryResponse {
    @Setter
    @Getter
    private String answer;

    @Setter
    @Getter
    private String sql;

    @Setter
    @Getter
    private List<Map<String, Object>> data;

    @Setter
    @Getter
    private String error;

}