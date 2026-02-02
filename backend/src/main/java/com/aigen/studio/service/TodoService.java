package com.aigen.studio.service;

import com.aigen.studio.dto.TodoItemDTO;
import com.aigen.studio.entity.GenerationJob;
import com.aigen.studio.entity.IRDocument;
import com.aigen.studio.repository.IRDocumentRepository;
import com.aigen.studio.repository.GenerationJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 待办事项服务
 * 从 IR 文档解析待办事项，并从 Job.logOutput 解析状态更新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final IRDocumentRepository irDocumentRepository;
    private final GenerationJobRepository generationJobRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据 IR 文档 ID 解析待办事项
     */
    public List<TodoItemDTO> parseTodosFromIR(Long irDocumentId) {
        IRDocument irDocument = irDocumentRepository.findById(irDocumentId)
                .orElseThrow(() -> new RuntimeException("IR Document not found: " + irDocumentId));

        List<TodoItemDTO> todos = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(irDocument.getContent());
            JsonNode modulesNode = rootNode.get("modules");

            if (modulesNode != null && modulesNode.isArray()) {
                for (JsonNode moduleNode : modulesNode) {
                    String moduleName = moduleNode.get("name").asText();
                    JsonNode featuresNode = moduleNode.get("features");

                    if (featuresNode != null && featuresNode.isArray()) {
                        for (JsonNode featureNode : featuresNode) {
                            TodoItemDTO todo = new TodoItemDTO();
                            todo.setId((long) todos.size());
                            todo.setTitle(featureNode.get("name").asText());
                            todo.setDescription(featureNode.has("description") ?
                                    featureNode.get("description").asText() : "");
                            todo.setStatus("pending");
                            todo.setOrder(todos.size());
                            todo.setModuleName(moduleName);
                            todo.setFeatureName(featureNode.get("name").asText());
                            todos.add(todo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse todos from IR document: {}", irDocumentId, e);
        }

        return todos;
    }

    /**
     * 根据 Job ID 获取待办事项列表（包含从 logOutput 解析的状态）
     */
    public List<TodoItemDTO> getTodosByJobId(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Generation Job not found: " + jobId));

        if (job.getIrDocumentId() == null) {
            log.warn("Job {} has no IR document", jobId);
            return new ArrayList<>();
        }

        // 从 IR 文档获取待办事项列表
        List<TodoItemDTO> todos = parseTodosFromIR(job.getIrDocumentId());

        // 从 logOutput 解析状态更新
        if (job.getLogOutput() != null && !job.getLogOutput().isEmpty()) {
            updateTodoStatusesFromLogs(todos, job.getLogOutput());
        }

        return todos;
    }

    /**
     * 从日志解析待办事项状态更新
     * 日志格式: "更新待办事项列表（X个待处理，Y个进行中，Z个已完成）"
     */
    private void updateTodoStatusesFromLogs(List<TodoItemDTO> todos, String logOutput) {
        Pattern pattern = Pattern.compile("更新待办事项列表\\((\\d+)个待处理，(\\d+)个进行中，(\\d+)个已完成\\)");
        
        // 查找所有待办事项状态更新
        Matcher matcher = pattern.matcher(logOutput);
        int lastPending = 0;
        int lastInProgress = 0;
        int lastCompleted = 0;
        
        while (matcher.find()) {
            lastPending = Integer.parseInt(matcher.group(1));
            lastInProgress = Integer.parseInt(matcher.group(2));
            lastCompleted = Integer.parseInt(matcher.group(3));
        }

        // 根据最后的统计信息更新待办事项状态
        int pendingCount = 0;
        int inProgressCount = 0;
        int completedCount = 0;

        for (TodoItemDTO todo : todos) {
            if (completedCount < lastCompleted) {
                todo.setStatus("completed");
                completedCount++;
            } else if (inProgressCount < lastInProgress) {
                todo.setStatus("in_progress");
                inProgressCount++;
            } else {
                todo.setStatus("pending");
            }
        }

        log.debug("Updated todo statuses: pending={}, in_progress={}, completed={}", 
            lastPending, lastInProgress, lastCompleted);
    }

    /**
     * 更新待办事项状态
     */
    public TodoItemDTO updateTodoStatus(Long irDocumentId, Long todoId, String status) {
        List<TodoItemDTO> todos = parseTodosFromIR(irDocumentId);
        if (todoId < todos.size()) {
            todos.get(todoId.intValue()).setStatus(status);
            return todos.get(todoId.intValue());
        }
        throw new RuntimeException("Todo not found: " + todoId);
    }

    /**
     * 更新 Job 的待办事项状态
     */
    public TodoItemDTO updateTodoStatusByJob(Long jobId, Long todoId, String status) {
        List<TodoItemDTO> todos = getTodosByJobId(jobId);
        for (TodoItemDTO todo : todos) {
            if (todo.getId().equals(todoId)) {
                todo.setStatus(status);
                return todo;
            }
        }
        throw new RuntimeException("Todo not found: " + todoId);
    }
}