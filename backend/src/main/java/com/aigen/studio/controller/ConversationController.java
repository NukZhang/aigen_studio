package com.aigen.studio.controller;

import com.aigen.studio.dto.ConfirmUnderstandingRequest;
import com.aigen.studio.dto.ConversationDTO;
import com.aigen.studio.dto.MessageDTO;
import com.aigen.studio.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;

    // ==================== 独立对话流程（新功能） ====================

    /**
     * 创建新的独立对话（空对话）
     */
    @PostMapping("/new")
    public ResponseEntity<ConversationDTO> createNewConversation(
            @RequestParam(required = false, defaultValue = "user") String createdBy) {
        log.info("Creating new conversation by user: {}", createdBy);
        ConversationDTO conversation = conversationService.createNewConversation(createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    /**
     * 获取独立对话详情
     */
    @GetMapping("/new/{id}")
    public ResponseEntity<ConversationDTO> getNewConversation(@PathVariable Long id) {
        ConversationDTO conversation = conversationService.getNewConversationById(id);
        return ResponseEntity.ok(conversation);
    }

    /**
     * 获取活跃的对话列表
     */
    @GetMapping("/active")
    public ResponseEntity<List<ConversationDTO>> getActiveConversations(
            @RequestParam(required = false, defaultValue = "user") String createdBy) {
        List<ConversationDTO> conversations = conversationService.getActiveConversations(createdBy);
        return ResponseEntity.ok(conversations);
    }

    /**
     * 发送消息到独立对话
     */
    @PostMapping("/new/{id}/messages")
    public ResponseEntity<MessageDTO> sendMessageToNewConversation(
            @PathVariable Long id,
            @RequestBody MessageDTO message) {
        log.info("Sending message to new conversation {}: {}", id, message.getContent());
        MessageDTO response = conversationService.sendMessageToNewConversation(id, message);
        return ResponseEntity.ok(response);
    }

    /**
     * 确认理解需求
     */
    @PostMapping("/new/{id}/confirm")
    public ResponseEntity<ConversationDTO> confirmUnderstanding(
            @PathVariable Long id,
            @RequestBody ConfirmUnderstandingRequest request) {
        log.info("Confirming understanding for conversation {}: {}", id, request.getConfirmed());
        ConversationDTO conversation = conversationService.confirmUnderstanding(id, request);
        return ResponseEntity.ok(conversation);
    }
}
