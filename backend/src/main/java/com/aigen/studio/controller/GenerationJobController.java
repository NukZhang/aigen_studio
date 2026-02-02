package com.aigen.studio.controller;

import com.aigen.studio.dto.ConversationDTO;
import com.aigen.studio.dto.GenerationJobDTO;
import com.aigen.studio.entity.GenerationJob;
import com.aigen.studio.service.ConversationService;
import com.aigen.studio.service.GenerationJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/generation-jobs")
@RequiredArgsConstructor
@Slf4j
public class GenerationJobController {

    private final GenerationJobService jobService;
    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<GenerationJobDTO> createJob(
            @RequestParam Long requirementId,
            @RequestParam Long irDocumentId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        log.info("Creating generation job for requirement: {}", requirementId);
        GenerationJobDTO created = jobService.createJob(requirementId, irDocumentId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, String>> executeJob(@PathVariable Long id) {
        log.info("Executing job: {}", id);
        jobService.executeJob(id);
        return ResponseEntity.ok(Map.of(
            "message", "Job execution started",
            "jobId", String.valueOf(id)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GenerationJobDTO> getJob(@PathVariable Long id) {
        return jobService.getJobById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{jobCode}")
    public ResponseEntity<GenerationJobDTO> getJobByCode(@PathVariable String jobCode) {
        return jobService.getJobByCode(jobCode)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/requirement/{requirementId}")
    public ResponseEntity<List<GenerationJobDTO>> getJobsByRequirementId(@PathVariable Long requirementId) {
        return ResponseEntity.ok(jobService.getJobsByRequirementId(requirementId));
    }

    @GetMapping
    public ResponseEntity<List<GenerationJobDTO>> getAllJobs(
            @RequestParam(required = false) GenerationJob.JobStatus status) {
        if (status != null) {
            return ResponseEntity.ok(jobService.getJobsByStatus(status));
        }
        return ResponseEntity.ok(jobService.getAllJobs());
    }

    @GetMapping("/{id}/delivery-logs")
    public ResponseEntity<String> getDeliveryLogs(@PathVariable Long id) {
        String logs = jobService.getDeliveryLogs(id);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{id}/conversation")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable Long id) {
        log.info("Getting conversation for job: {}", id);
        ConversationDTO conversation = conversationService.getConversationByJobId(id);
        return ResponseEntity.ok(conversation);
    }
}