package com.aigen.studio.controller;

import com.aigen.studio.dto.PreviewStatusDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.service.PreviewService;
import com.aigen.studio.service.PromptTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:conversation-sdac-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "aigen.preview.allow-unverified-preview=false"
})
@AutoConfigureMockMvc
class ConversationSdacFlowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationRepository conversationRepository;

    @MockBean
    private PromptTaskService promptTaskService;

    @MockBean
    private PreviewService previewService;

    @BeforeEach
    void setUp() {
        conversationRepository.deleteAll();
    }

    @Test
    void i1AndI6UiDesignTransitionsToUiDesigningAndReturnsUiSpecAndPrototype() throws Exception {
        Conversation conversation = newConversation(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation.setUnderstandingConfirmed(true);
        conversation.setMe2aiContractJson("{\"projectGoal\":\"目标\"}");
        conversation = conversationRepository.save(conversation);

        mockMvc.perform(post("/conversations/{id}/ui/design", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.stage").value("UI_DESIGNING"))
                .andExpect(jsonPath("$.uiSpec.pages", hasSize(1)))
                .andExpect(jsonPath("$.uiSpec.routes", hasSize(1)))
                .andExpect(jsonPath("$.uiSpec.globalStates", hasSize(1)))
                .andExpect(jsonPath("$.uiSpec.interactions", hasSize(1)))
                .andExpect(jsonPath("$.prototypePath", not(blankOrNullString())));
    }

    @Test
    void i1UiDesignBlocksWhenReqGateNotPassed() throws Exception {
        Conversation conversation = newConversation(ConversationStage.UNDERSTANDING);
        conversation.setUnderstandingConfirmed(false);
        conversation = conversationRepository.save(conversation);

        mockMvc.perform(post("/conversations/{id}/ui/design", conversation.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void i2UnderstandingParseReturnsClarificationPayloadOrContract() throws Exception {
        Conversation askConversation = newConversation(ConversationStage.UNDERSTANDING);
        askConversation.setUserRequirement("信息不足的需求");
        askConversation = conversationRepository.save(askConversation);

        when(promptTaskService.understandRequirement("信息不足的需求")).thenReturn("""
                <REQUIREMENT_GATE>
                NEXT_ACTION: ASK_CLARIFICATION
                MISSING_INFO_COUNT: 3
                </REQUIREMENT_GATE>
                <CLARIFICATION_PAYLOAD>
                {
                  "questions": [
                    {"id":"q1","question":"平台？","options":["H5","小程序"]},
                    {"id":"q2","question":"用户？","options":["游客","注册用户"]},
                    {"id":"q3","question":"登录？","options":["需要","不需要"]}
                  ]
                }
                </CLARIFICATION_PAYLOAD>
                """);

        mockMvc.perform(post("/conversations/{id}/understanding/parse", askConversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("ASK_CLARIFICATION"))
                .andExpect(jsonPath("$.questions", hasSize(3)))
                .andExpect(jsonPath("$.questions[0].options", hasSize(2)));

        Conversation confirmConversation = newConversation(ConversationStage.UNDERSTANDING);
        confirmConversation.setUserRequirement("信息充分的需求");
        confirmConversation = conversationRepository.save(confirmConversation);

        when(promptTaskService.understandRequirement("信息充分的需求")).thenReturn("""
                <REQUIREMENT_GATE>
                NEXT_ACTION: READY_FOR_CONFIRM
                MISSING_INFO_COUNT: 0
                </REQUIREMENT_GATE>
                ## 需求契约卡
                ### 项目目标
                - 构建一个任务管理平台
                ### 平台与目标用户
                - Web 管理端，面向团队成员
                ### 核心功能
                - 任务创建
                - 看板视图
                - 任务分配
                ### 关键业务规则
                - 仅负责人可关闭任务
                ### 范围外事项
                - 暂不支持移动端原生应用
                ### 非功能要求
                - 首屏 2 秒内可交互
                ### 验收标准
                - 可创建并分配任务
                ### 风险与待确认项
                - 无
                """);

        mockMvc.perform(post("/conversations/{id}/understanding/parse", confirmConversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("READY_FOR_CONFIRM"))
                .andExpect(jsonPath("$.contract.projectGoal", not(blankOrNullString())))
                .andExpect(jsonPath("$.contract.platformAndTargetUsers", not(blankOrNullString())))
                .andExpect(jsonPath("$.contract.coreFeatures", hasSize(3)))
                .andExpect(jsonPath("$.contract.keyBusinessRules", hasSize(1)))
                .andExpect(jsonPath("$.contract.nonFunctionalRequirements", hasSize(1)))
                .andExpect(jsonPath("$.contract.acceptanceCriteria", hasSize(1)));
    }

    @Test
    void i3I7I8ConfirmUiAndBuildPlanAndVerifyWritesEvidence() throws Exception {
        Conversation conversation = newConversation(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation.setUnderstandingConfirmed(true);
        conversation.setMe2aiContractJson("""
                {"projectGoal":"目标","platformAndTargetUsers":"用户","coreFeatures":["A"],"keyBusinessRules":["B"],"scopeExclusions":["C"],"nonFunctionalRequirements":["D"],"acceptanceCriteria":["E"],"risksAndOpenQuestions":"无"}
                """);
        conversation = conversationRepository.save(conversation);

        mockMvc.perform(post("/conversations/{id}/understanding/confirm", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("UNDERSTANDING_CONFIRMED"));

        Conversation afterConfirm = conversationRepository.findById(conversation.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(afterConfirm.getMe2aiContractJson());
        org.junit.jupiter.api.Assertions.assertNotNull(afterConfirm.getMe2aiConfirmedAt());

        mockMvc.perform(post("/conversations/{id}/implementation/plan", conversation.getId()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/conversations/{id}/ui/design", conversation.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/conversations/{id}/ui/confirm", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("UI_CONFIRMED"));

        mockMvc.perform(post("/conversations/{id}/implementation/plan", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope", hasSize(1)))
                .andExpect(jsonPath("$.nonGoals", hasSize(1)))
                .andExpect(jsonPath("$.filesToChange", hasSize(2)))
                .andExpect(jsonPath("$.verifications", hasSize(2)));

        mockMvc.perform(post("/conversations/{id}/implementation/verify", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passed": false,
                                  "verifications": [
                                    {"cmd":"mvn test","status":"FAIL","summary":"1 case failed"}
                                  ],
                                  "artifacts": ["backend/target/surefire-reports"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("FAIL"))
                .andExpect(jsonPath("$.manifestPath", not(blankOrNullString())))
                .andExpect(jsonPath("$.verifications", hasSize(1)));

        Conversation afterVerify = conversationRepository.findById(conversation.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(afterVerify.getEvidenceManifestPath());
        org.junit.jupiter.api.Assertions.assertNotNull(afterVerify.getGateStatusJson());
    }

    @Test
    void i10PreviewStartRequiresEvidenceBindingWhenUnverifiedPreviewDisabled() throws Exception {
        Conversation conversation = newConversation(ConversationStage.READY_TO_START);
        conversation.setUnderstandingConfirmed(true);
        conversation.setUiConfirmed(true);
        conversation = conversationRepository.save(conversation);

        mockMvc.perform(post("/conversations/{id}/preview/start", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evidenceRef":"evidence/manifest.json"}
                                """))
                .andExpect(status().isConflict());

        verifyNoInteractions(previewService);
    }

    @Test
    void previewStartPassesWhenEvidenceRefMatches() throws Exception {
        Conversation conversation = newConversation(ConversationStage.READY_TO_START);
        conversation.setEvidenceManifestPath("evidence/manifest.json");
        conversation = conversationRepository.save(conversation);

        when(previewService.startPreview(anyLong())).thenReturn(new PreviewStatusDTO(
                conversation.getId(),
                true,
                true,
                true,
                3000,
                8080,
                "http://localhost:3000",
                "http://localhost:8080",
                "ok"
        ));

        mockMvc.perform(post("/conversations/{id}/preview/start", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evidenceRef":"evidence/manifest.json"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running", equalTo(true)));
    }

    private Conversation newConversation(ConversationStage stage) {
        Conversation conversation = new Conversation();
        conversation.setProjectName("SDAC Test");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(stage);
        conversation.setUserRequirement("构建一个任务管理应用");
        conversation.setAiUnderstanding("initial");
        conversation.setUnderstandingConfirmed(false);
        conversation.setUiConfirmed(false);
        conversation.setGateStatusJson(writeGateStatus(Map.of(
                "REQ", "PENDING",
                "UI", "PENDING",
                "IMP", "PENDING",
                "PREVIEW", "PENDING"
        )));
        return conversation;
    }

    private String writeGateStatus(Map<String, String> gate) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : gate.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
        }
        builder.append("}");
        return builder.toString();
    }
}
