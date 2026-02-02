package com.aigen.studio.controller;

import com.aigen.studio.dto.PreviewStatusDTO;
import com.aigen.studio.service.PreviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/preview")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewService previewService;

    @PostMapping("/conversation/{conversationId}/start")
    public ResponseEntity<PreviewStatusDTO> startPreview(@PathVariable Long conversationId) {
        log.info("Starting preview for conversation {}", conversationId);
        return ResponseEntity.ok(previewService.startPreview(conversationId));
    }

    @PostMapping("/conversation/{conversationId}/stop")
    public ResponseEntity<PreviewStatusDTO> stopPreview(@PathVariable Long conversationId) {
        log.info("Stopping preview for conversation {}", conversationId);
        return ResponseEntity.ok(previewService.stopPreview(conversationId));
    }

    @PostMapping("/conversation/{conversationId}/restart")
    public ResponseEntity<PreviewStatusDTO> restartPreview(@PathVariable Long conversationId) {
        log.info("Restarting preview for conversation {}", conversationId);
        return ResponseEntity.ok(previewService.restartPreview(conversationId));
    }

    @GetMapping("/conversation/{conversationId}/status")
    public ResponseEntity<PreviewStatusDTO> getStatus(@PathVariable Long conversationId) {
        return ResponseEntity.ok(previewService.getStatus(conversationId));
    }
}
