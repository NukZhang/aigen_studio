package com.aigen.studio.controller;

import com.aigen.studio.dto.ConversationDTO;
import com.aigen.studio.service.ConversationService;
import com.aigen.studio.service.UIPrototypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * UI 原型控制器
 * 提供 UI 原型的生成、读取、确认等 API
 */
@RestController
@RequestMapping("/ui-prototype")
@RequiredArgsConstructor
@Slf4j
public class UIPrototypeController {

    private final UIPrototypeService uiPrototypeService;
    private final ConversationService conversationService;

    /**
     * 获取 UI 原型
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<Map<String, Object>> getUIPrototype(@PathVariable Long conversationId) {
        log.info("Getting UI prototype for conversation: {}", conversationId);

        String htmlContent = uiPrototypeService.getUIPrototype(conversationId);
        ConversationDTO conversation = conversationService.getNewConversationById(conversationId);

        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("htmlContent", htmlContent);
        response.put("uiConfirmed", conversation.getUiConfirmed());
        response.put("uiPrototypePath", conversation.getUiPrototypePath());

        return ResponseEntity.ok(response);
    }

    /**
     * 确认 UI 设计
     */
    @PostMapping("/{conversationId}/confirm")
    public ResponseEntity<ConversationDTO> confirmUIPrototype(@PathVariable Long conversationId) {
        log.info("Confirming UI prototype for conversation: {}", conversationId);

        ConversationDTO conversation = conversationService.confirmUIPrototype(conversationId);
        return ResponseEntity.ok(conversation);
    }

    /**
     * 重新生成 UI 原型
     */
    @PostMapping("/{conversationId}/regenerate")
    public ResponseEntity<Map<String, String>> regenerateUIPrototype(@PathVariable Long conversationId) {
        log.info("Regenerating UI prototype for conversation: {}", conversationId);

        uiPrototypeService.regenerateUIPrototype(conversationId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "UI 原型重新生成中...");
        response.put("conversationId", String.valueOf(conversationId));

        return ResponseEntity.ok(response);
    }
}