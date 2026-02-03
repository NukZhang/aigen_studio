# Remove Job Flow Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove Job/Requirement/IR/Artifact flows entirely, keep only conversation flow, and clean the database accordingly.

**Architecture:** Remove Job-related controllers/services/entities/repos/DTOs and frontend routes/pages/APIs, update conversation flow to use `iflow.sdk.output-dir` for generated code, and add a startup cleanup runner to drop Job/Requirement/IR/Artifact tables and legacy columns in `conversations`.

**Tech Stack:** Spring Boot 3.2, Spring Data JPA, Vue 3 + Vite, TypeScript.

### Task 1: Add failing test for conversation output path config

**Files:**
- Modify: `backend/src/test/java/com/aigen/studio/service/ConversationReadyStageTest.java`

**Step 1: Write the failing test**

```java
@Test
void confirmUnderstandingUsesConfiguredOutputDir(@TempDir Path tmp) {
    Conversation conversation = new Conversation();
    conversation.setProjectName("Test");
    conversation.setUserRequirement("Generate a demo app");
    conversation.setAiUnderstanding("Understood requirements");
    conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
    conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
    conversation.setUnderstandingConfirmed(false);
    conversation = conversationRepository.save(conversation);

    conversationService.confirmUnderstanding(conversation.getId(), new ConfirmUnderstandingRequest(true, null));

    Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
    assertTrue(updated.getGeneratedCodePath().startsWith(tmp.toString()));
}
```

**Step 2: Run test to verify it fails**

Run: `JAVA_HOME="$([ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17)" mvn -q -Dtest=ConversationReadyStageTest test`
Expected: FAIL (path points to `java.io.tmpdir` not configured output dir)

### Task 2: Use configured output dir for conversation generation

**Files:**
- Modify: `backend/src/main/java/com/aigen/studio/service/ConversationService.java`

**Step 1: Implement minimal change**

```java
@Value("${iflow.sdk.output-dir:./output}")
private String outputDir;

Path outputPath = Paths.get(outputDir, "conversation-" + conversationId);
Files.createDirectories(outputPath);
```

**Step 2: Run test to verify it passes**

Run: `JAVA_HOME="$([ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17)" mvn -q -Dtest=ConversationReadyStageTest test`
Expected: PASS

### Task 3: Remove backend Job/Requirement/IR/Artifact components

**Files:**
- Delete: `backend/src/main/java/com/aigen/studio/controller/RequirementController.java`
- Delete: `backend/src/main/java/com/aigen/studio/controller/GenerationJobController.java`
- Delete: `backend/src/main/java/com/aigen/studio/controller/ArtifactController.java`
- Delete: `backend/src/main/java/com/aigen/studio/controller/IRDocumentController.java`
- Delete: `backend/src/main/java/com/aigen/studio/entity/Requirement.java`
- Delete: `backend/src/main/java/com/aigen/studio/entity/GenerationJob.java`
- Delete: `backend/src/main/java/com/aigen/studio/entity/IRDocument.java`
- Delete: `backend/src/main/java/com/aigen/studio/entity/Artifact.java`
- Delete: `backend/src/main/java/com/aigen/studio/repository/RequirementRepository.java`
- Delete: `backend/src/main/java/com/aigen/studio/repository/GenerationJobRepository.java`
- Delete: `backend/src/main/java/com/aigen/studio/repository/IRDocumentRepository.java`
- Delete: `backend/src/main/java/com/aigen/studio/repository/ArtifactRepository.java`
- Delete: `backend/src/main/java/com/aigen/studio/service/RequirementService.java`
- Delete: `backend/src/main/java/com/aigen/studio/service/GenerationJobService.java`
- Delete: `backend/src/main/java/com/aigen/studio/service/ArtifactService.java`
- Delete: `backend/src/main/java/com/aigen/studio/service/IFlowGenerationService.java`
- Delete: `backend/src/main/java/com/aigen/studio/service/TodoService.java`
- Delete: `backend/src/main/java/com/aigen/studio/dto/RequirementDTO.java`
- Delete: `backend/src/main/java/com/aigen/studio/dto/CreateRequirementRequest.java`
- Delete: `backend/src/main/java/com/aigen/studio/dto/UpdateRequirementRequest.java`
- Delete: `backend/src/main/java/com/aigen/studio/dto/GenerationJobDTO.java`
- Delete: `backend/src/main/java/com/aigen/studio/dto/IRDocumentDTO.java`
- Delete: `backend/src/main/java/com/aigen/studio/dto/ArtifactDTO.java`
- Modify: `backend/src/main/java/com/aigen/studio/controller/ConversationController.java` (remove job-based endpoints and Todo wiring)
- Modify: `backend/src/main/java/com/aigen/studio/service/ConversationService.java` (remove job-based conversation logic and repositories)
- Modify: `backend/src/main/java/com/aigen/studio/dto/ConversationDTO.java` (remove jobId/projectId/todos fields)
- Modify: `backend/src/main/java/com/aigen/studio/entity/Conversation.java` (remove requirementId/jobId fields)
- Modify: `backend/src/main/java/com/aigen/studio/service/FileService.java` (remove job-based file methods)
- Modify: `backend/src/main/java/com/aigen/studio/controller/FileController.java` (remove job-based endpoints)
- Modify: `backend/src/main/java/com/aigen/studio/config/OpenApiConfig.java` if it references removed controllers

**Step 1: Remove code (one class per edit)**
Delete the listed files, then strip imports/usages in Conversation/File services/controllers.

**Step 2: Update tests**
Remove or update any tests referencing job/requirement flows.

### Task 4: Add DB cleanup runner

**Files:**
- Create: `backend/src/main/java/com/aigen/studio/config/SchemaCleanupConfig.java`

**Step 1: Write failing test (lightweight)**
Skip automated DB schema test; rely on manual verification via startup logs.

**Step 2: Implement cleanup runner**

```java
@Configuration
@RequiredArgsConstructor
public class SchemaCleanupConfig {
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void cleanup() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS artifacts");
        jdbcTemplate.execute("DROP TABLE IF EXISTS generation_jobs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS ir_documents");
        jdbcTemplate.execute("DROP TABLE IF EXISTS requirements");
        jdbcTemplate.execute("ALTER TABLE conversations DROP COLUMN IF EXISTS job_id");
        jdbcTemplate.execute("ALTER TABLE conversations DROP COLUMN IF EXISTS requirement_id");
    }
}
```

### Task 5: Remove frontend Job/Requirement/Artifact flows

**Files:**
- Delete: `frontend/src/views/Requirements.vue`
- Delete: `frontend/src/views/RequirementDetail.vue`
- Delete: `frontend/src/views/Jobs.vue`
- Delete: `frontend/src/views/JobDetail.vue`
- Delete: `frontend/src/views/Artifacts.vue`
- Delete: `frontend/src/api/requirement.ts`
- Delete: `frontend/src/api/irDocument.ts`
- Delete: `frontend/src/api/artifact.ts`
- Modify: `frontend/src/api/job.ts` (remove job-related APIs; keep conversation + file APIs)
- Modify: `frontend/src/router/index.ts` (remove routes)
- Modify: `frontend/src/App.vue` (remove sidebar items)
- Modify: `frontend/src/views/Workspace.vue` (remove jobId logic; only conversation flow)
- Modify: `frontend/src/views/FileBrowser.vue` (remove job props/actions)
- Modify: `frontend/src/views/CodeEditor.vue` (remove job props/actions)

**Step 1: Remove files and update imports**
Delete listed files and remove references from router and App.

**Step 2: Update Workspace UI**
Remove job-based placeholders and actions; always allow sending if conversation exists.

### Task 6: Run tests

**Step 1: Backend targeted tests**
Run: `JAVA_HOME="$([ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17)" mvn -q test`
Expected: PASS

**Step 2: Frontend lint/build (optional)**
Run: `npm run lint` and `npm run build` in `frontend/` if dependencies installed.

### Task 7: Document changes

**Files:**
- Modify: `docs/QUICKSTART.md` and `docs/DEPLOYMENT.md` to remove Job/Requirement references.

**Step 1: Update docs**
Remove references to jobs/requirements/artifacts/IR flows.

