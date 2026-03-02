package com.aigen.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.aigen.studio.config.FeishuProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 飞书机器人服务
 * 使用 WebSocket 长连接接收消息，调用 iFlow API 进行回复
 */
@Slf4j
@Service
public class FeishuBotService {

    private final FeishuProperties feishuProperties;
    private final IFlowApiService iFlowApiService;

    private com.lark.oapi.ws.Client wsClient;
    private Client apiClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public FeishuBotService(FeishuProperties feishuProperties, IFlowApiService iFlowApiService) {
        this.feishuProperties = feishuProperties;
        this.iFlowApiService = iFlowApiService;
    }

    @PostConstruct
    public void start() {
        if (!feishuProperties.isEnabled()) {
            log.info("Feishu bot is disabled, skipping initialization");
            return;
        }

        if (feishuProperties.getAppId() == null || feishuProperties.getAppId().isEmpty()) {
            log.warn("Feishu App ID is not configured, skipping initialization");
            return;
        }

        if (feishuProperties.getAppSecret() == null || feishuProperties.getAppSecret().isEmpty()) {
            log.warn("Feishu App Secret is not configured, skipping initialization");
            return;
        }

        // 创建 API 客户端
        apiClient = Client.newBuilder(feishuProperties.getAppId(), feishuProperties.getAppSecret())
                .build();

        // 异步启动飞书 WebSocket 客户端
        executorService.submit(() -> {
            try {
                initFeishuWsClient();
            } catch (Exception e) {
                log.error("Failed to initialize Feishu WebSocket client", e);
            }
        });
    }

    private void initFeishuWsClient() {
        log.info("Initializing Feishu WebSocket client...");
        log.info("App ID: {}...", feishuProperties.getAppId().substring(0, Math.min(8, feishuProperties.getAppId().length())));

        running.set(true);

        // 创建事件处理器
        EventDispatcher eventHandler = EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) throws Exception {
                        handleIncomingMessage(event);
                    }
                })
                .build();

        // 创建飞书 WebSocket 客户端
        wsClient = new com.lark.oapi.ws.Client.Builder(feishuProperties.getAppId(), feishuProperties.getAppSecret())
                .eventHandler(eventHandler)
                .build();

        // 启动客户端（阻塞）
        wsClient.start();
    }

    /**
     * 处理接收到的消息
     */
    private void handleIncomingMessage(P2MessageReceiveV1 event) {
        try {
            String messageId = event.getEvent().getMessage().getMessageId();
            String chatId = event.getEvent().getMessage().getChatId();
            String msgType = event.getEvent().getMessage().getMessageType();
            String content = event.getEvent().getMessage().getContent();
            String senderId = event.getEvent().getSender().getSenderId().getOpenId();

            log.info("Received message - chatId: {}, msgType: {}, sender: {}", chatId, msgType, senderId);

            // 只处理文本消息
            if (!"text".equals(msgType)) {
                log.debug("Ignoring non-text message type: {}", msgType);
                return;
            }

            // 解析文本内容
            String textContent = extractTextContent(content);
            if (textContent == null || textContent.trim().isEmpty()) {
                log.debug("Ignoring empty text message");
                return;
            }

            log.info("Processing text message: {}", textContent.length() > 100 ? textContent.substring(0, 100) + "..." : textContent);

            // 调用 iFlow API 获取回复
            String reply = iFlowApiService.chat(textContent);

            // 发送回复
            sendReply(chatId, reply);

        } catch (Exception e) {
            log.error("Error handling incoming message", e);
        }
    }

    /**
     * 从 JSON 内容中提取文本
     */
    private String extractTextContent(String content) {
        try {
            if (content == null) return null;
            // 飞书文本消息格式: {"text":"消息内容"}
            JsonNode node = objectMapper.readTree(content);
            return node.path("text").asText();
        } catch (Exception e) {
            log.warn("Failed to parse message content: {}", content, e);
            return content;
        }
    }

    /**
     * 发送回复消息
     */
    private void sendReply(String chatId, String content) {
        try {
            // 构建回复消息内容
            String replyContent = String.format("{\"text\":\"%s\"}", escapeJson(content));

            // 使用飞书 API 发送消息
            CreateMessageResp resp = apiClient.im().message().create(
                    CreateMessageReq.newBuilder()
                            .receiveIdType("chat_id")
                            .createMessageReqBody(
                                    CreateMessageReqBody.newBuilder()
                                            .receiveId(chatId)
                                            .msgType("text")
                                            .content(replyContent)
                                            .build()
                            )
                            .build()
            );

            if (resp.getCode() != 0) {
                log.error("Failed to send reply, code: {}, msg: {}", resp.getCode(), resp.getMsg());
            } else {
                log.info("Reply sent successfully to chat: {}", chatId);
            }

        } catch (Exception e) {
            log.error("Error sending reply message", e);
        }
    }

    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        // WebSocket 客户端没有 stop 方法，通过关闭连接来停止
        log.info("Feishu bot service stopped");
        executorService.shutdown();
    }
}