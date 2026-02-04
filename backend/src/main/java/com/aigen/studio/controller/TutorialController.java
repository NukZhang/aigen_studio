package com.aigen.studio.controller;

import com.aigen.studio.service.TutorialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 教程控制器
 * 提供教程文档浏览和读取功能
 */
@Slf4j
@RestController
@RequestMapping("/tutorials")
@RequiredArgsConstructor
public class TutorialController {

    private final TutorialService tutorialService;

    /**
     * 获取教程目录树
     */
    @GetMapping("/tree")
    public ResponseEntity<List<Map<String, Object>>> getTutorialTree() {
        log.info("Getting tutorial tree");
        List<Map<String, Object>> tree = tutorialService.getTutorialTree();
        return ResponseEntity.ok(tree);
    }

    /**
     * 获取教程文件内容
     */
    @GetMapping("/content")
    public ResponseEntity<Map<String, Object>> getTutorialContent(@RequestParam String path) {
        log.info("Getting tutorial content for path: {}", path);

        try {
            String content = tutorialService.getTutorialContent(path);
            List<Map<String, Object>> headings = tutorialService.extractHeadings(content);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "path", path,
                    "content", content,
                    "headings", headings
            ));
        } catch (Exception e) {
            log.error("Failed to get tutorial content", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}