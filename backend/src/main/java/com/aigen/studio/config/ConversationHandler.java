package com.aigen.studio.config;

import com.aigen.studio.dto.StreamMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class ConversationHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Received message: {}", payload);

        // 模拟流式响应
        executorService.submit(() -> {
            try {
                log.info("Starting streaming response for session: {}", session.getId());
                log.info("Session is open: {}", session.isOpen());
                
                // 发送文本消息（模拟 AI 逐字输出）
                String responseText = "这是一个模拟的流式响应。AI 正在逐字输出回复内容，就像 ChatGPT 那样。";
                sendStreamText(session, responseText);

                // 发送完成消息
                log.info("Sending finish message");
                StreamMessageDTO finishMessage = StreamMessageDTO.finish();
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(finishMessage)));
                log.info("Streaming response completed");

            } catch (Exception e) {
                log.error("Error handling message", e);
                try {
                    StreamMessageDTO errorMessage = StreamMessageDTO.error("处理消息时出错: " + e.getMessage());
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
                } catch (Exception ex) {
                    log.error("Error sending error message", ex);
                }
            }
        });
    }

    /**
     * 发送流式文本（逐字发送）
     */
    private void sendStreamText(WebSocketSession session, String text) throws Exception {
        log.info("Starting to send stream text: {} chars", text.length());
        String[] chars = text.split("");
        
        for (int i = 0; i < chars.length; i++) {
            if (!session.isOpen()) {
                log.warn("Session closed at character {}, stopping stream", i);
                break;
            }
            
            StreamMessageDTO streamMessage = StreamMessageDTO.text(chars[i]);
            String jsonMessage = objectMapper.writeValueAsString(streamMessage);
            
            log.debug("Sending character {}/{}: {}", i + 1, chars.length, chars[i]);
            session.sendMessage(new TextMessage(jsonMessage));
            Thread.sleep(50);
        }
        
        log.info("Finished sending stream text: {} characters", chars.length);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error", exception);
    }
}