package com.aigen.studio.controller;

import com.aigen.studio.dto.ModelDTO;
import com.aigen.studio.sdk.ModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型控制器
 * 提供 AI 模型相关的 API 接口
 */
@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
@Slf4j
public class ModelController {

    private final ModelService modelService;

    /**
     * 获取可用的模型列表
     *
     * @return 模型列表
     */
    @GetMapping
    public ResponseEntity<List<ModelDTO>> getAvailableModels() {
        log.info("GET /models - Fetching available models");
        List<ModelDTO> models = modelService.getAvailableModels();
        return ResponseEntity.ok(models);
    }
}