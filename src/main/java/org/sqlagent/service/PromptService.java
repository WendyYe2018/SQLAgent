package org.sqlagent.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class PromptService {

    public String loadPrompt(String fileName) {

        ClassPathResource resource =
                new ClassPathResource(
                        "prompts/" + fileName);

        try {
            return new String(
                    resource.getInputStream()
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}