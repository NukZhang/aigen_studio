package com.aigen.studio.sdk;

import com.aigen.studio.dto.ModelDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型服务
 * 提供 AI 模型列表的获取功能
 * 通过 ICodingService 接口获取模型列表
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelService {

    private final ICodingService codingService;

    /**
     * 获取可用的模型列表
     * 通过 ICodingService 接口获取模型列表
     *
     * @return 模型列表
     */
    public List<ModelDTO> getAvailableModels() {
        log.info("Fetching available models using ICodingService");
        return codingService.getAvailableModels();
    }

    /**
     * 根据 ID 获取模型
     *
     * @param modelId 模型ID
     * @return 模型信息
     */
    public ModelDTO getModelById(String modelId) {
        return getAvailableModels().stream()
                .filter(model -> model.getId().equals(modelId))
                .findFirst()
                .orElse(null);
    }
}