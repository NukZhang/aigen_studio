package com.aigen.studio.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "gitlab")
public class GitLabProperties {

    private String url;
    private String token;
    private String basePath = "AIGen";
    private String defaultBranch = "main";
    private int timeout = 600; // seconds

    private final RestTemplate restTemplate = new RestTemplate();

    public ResponseEntity<Map> callGitLabApi(String url, HttpMethod method, Object body) {
        return callGitLabApiForMap(url, method, body);
    }

    public ResponseEntity<Map> callGitLabApiForMap(String url, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("PRIVATE-TOKEN", token);

        HttpEntity<?> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, request, String.class);
            
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                Map map = mapper.readValue(response.getBody(), Map.class);
                return new ResponseEntity<>(map, response.getHeaders(), response.getStatusCode());
            }
            
            return new ResponseEntity<>(new HashMap<>(), response.getHeaders(), response.getStatusCode());
            
        } catch (Exception e) {
            throw new RuntimeException("GitLab API call failed", e);
        }
    }
}