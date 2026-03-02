package com.aigen.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * iFlow API 调用服务
 * 用于调用 iFlow 平台的 AI 模型进行对话
 */
@Slf4j
@Service
public class IFlowApiService {

    private static final String IFLOW_API_URL = "https://apis.iflow.cn/v1/chat/completions";

    @Value("${iflow.api.key:}")
    private String apiKey;

    @Value("${iflow.api.model:qwen3-coder-plus}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 会话历史缓存（简单实现，生产环境应使用 Redis）
    private final List<Message> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 10;

    public IFlowApiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 发送消息到 iFlow API 并获取回复
     */
    public String chat(String userMessage) {
        return chat(userMessage, null);
    }

    /**
     * 发送消息到 iFlow API 并获取回复（支持系统提示）
     */
    public String chat(String userMessage, String systemPrompt) {
        try {
            // 构建请求体
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("stream", false);

            // 构建消息数组
            ArrayNode messages = requestBody.putArray("messages");

            // 添加系统提示
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                ObjectNode systemMsg = messages.addObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
            } else {
                // 默认系统提示
                ObjectNode systemMsg = messages.addObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是一个专业的 AI 编程助手，擅长代码生成、代码审查和技术问答。请用中文回答问题。");
            }

            // 添加历史消息
            synchronized (conversationHistory) {
                for (Message msg : conversationHistory) {
                    ObjectNode historyMsg = messages.addObject();
                    historyMsg.put("role", msg.role);
                    historyMsg.put("content", msg.content);
                }
            }

            // 添加当前用户消息
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            log.info("Sending request to iFlow API with model: {}", model);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                    IFLOW_API_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                JsonNode choices = responseJson.path("choices");

                if (choices.isArray() && choices.size() > 0) {
                    String assistantMessage = choices.get(0).path("message").path("content").asText();

                    // 保存对话历史
                    addToHistory("user", userMessage);
                    addToHistory("assistant", assistantMessage);

                    log.info("Received response from iFlow API, length: {}", assistantMessage.length());
                    return assistantMessage;
                }
            }

            log.error("Unexpected response from iFlow API: {}", response.getBody());
            return "抱歉，AI 服务返回了异常响应，请稍后重试。";

        } catch (Exception e) {
            log.error("Error calling iFlow API", e);
            return "抱歉，调用 AI 服务时发生错误：" + e.getMessage();
        }
    }

    /**
     * 清除对话历史
     */
    public void clearHistory() {
        synchronized (conversationHistory) {
            conversationHistory.clear();
            log.info("Conversation history cleared");
        }
    }

    private void addToHistory(String role, String content) {
        synchronized (conversationHistory) {
            conversationHistory.add(new Message(role, content));
            // 保持历史记录在限制范围内
            while (conversationHistory.size() > MAX_HISTORY) {
                conversationHistory.remove(0);
            }
        }
    }

    private static class Message {
        String role;
        String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
