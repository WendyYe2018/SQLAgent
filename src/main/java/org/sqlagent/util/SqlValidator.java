package org.sqlagent.util;

import org.springframework.stereotype.Component;

@Component
public class SqlValidator {

    public static  void validate(String sql){

        String s = sql.toLowerCase();

        if(s.contains("delete")
                || s.contains("update")
                || s.contains("insert")
                || s.contains("drop")
                || s.contains("truncate")){

            throw new RuntimeException(
                    "Unsafe SQL"
            );
        }
    }
}