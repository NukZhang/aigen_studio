package com.aigen.studio.service;

import com.aigen.studio.dto.ConversationDTO;
import com.aigen.studio.dto.MessageDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTitleTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private PromptTaskService promptTaskService;

    @Mock
    private CodeGenerationService codeGenerationService;

    @Mock
    private UIPrototypeService uiPrototypeService;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void autoGeneratesShortTitleAfterRequirementUnderstanding() {
        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setProjectName("新对话");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.NEED_INPUT);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(promptTaskService.understandRequirement(any(String.class))).thenReturn("understood");

        MessageDTO userMessage = new MessageDTO();
        userMessage.setContent("我想做一个库存管理系统，支持商品管理和库存预警");

        conversationService.sendMessageToNewConversation(1L, userMessage);

        assertEquals("库存管理系统", conversation.getProjectName());
        assertEquals(ConversationStage.UNDERSTANDING_CONFIRMED, conversation.getStage());
    }

    @Test
    void keepsExistingCustomTitleWhenUnderstandingRequirement() {
        Conversation conversation = new Conversation();
        conversation.setId(2L);
        conversation.setProjectName("我的自定义标题");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.NEED_INPUT);

        when(conversationRepository.findById(2L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(promptTaskService.understandRequirement(any(String.class))).thenReturn("understood");

        MessageDTO userMessage = new MessageDTO();
        userMessage.setContent("帮我做一个任务管理系统");

        conversationService.sendMessageToNewConversation(2L, userMessage);

        assertEquals("我的自定义标题", conversation.getProjectName());
    }

    @Test
    void updatesConversationTitleManually() {
        Conversation conversation = new Conversation();
        conversation.setId(3L);
        conversation.setProjectName("旧标题");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.NEED_INPUT);

        when(conversationRepository.findById(3L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(3L)).thenReturn(List.of());

        ConversationDTO updated = conversationService.updateConversationTitle(3L, "  新标题  ");

        assertEquals("新标题", updated.getProjectName());
        assertEquals("新标题", conversation.getProjectName());
    }

    @Test
    void rejectsBlankConversationTitleUpdate() {
        Conversation conversation = new Conversation();
        conversation.setId(4L);
        conversation.setProjectName("旧标题");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.NEED_INPUT);

        when(conversationRepository.findById(4L)).thenReturn(Optional.of(conversation));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> conversationService.updateConversationTitle(4L, "   "));

        assertEquals("Conversation title cannot be blank", exception.getMessage());
    }

    @Test
    void entersClarifyingStageWhenUnderstandingRequiresMoreInfo() {
        Conversation conversation = new Conversation();
        conversation.setId(5L);
        conversation.setProjectName("新对话");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.NEED_INPUT);

        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(promptTaskService.understandRequirement(any(String.class))).thenReturn(
                "<REQUIREMENT_GATE>\n" +
                "NEXT_ACTION: ASK_CLARIFICATION\n" +
                "MISSING_INFO_COUNT: 2\n" +
                "</REQUIREMENT_GATE>\n\n" +
                "1. 目标平台是微信还是 H5？\n" +
                "2. 排行榜按什么规则计分？"
        );

        MessageDTO userMessage = new MessageDTO();
        userMessage.setContent("做一个春节祝福和排行榜小程序");

        MessageDTO response = conversationService.sendMessageToNewConversation(5L, userMessage);

        assertEquals(ConversationStage.CLARIFYING, conversation.getStage());
        assertTrue(response.getContent().contains("还需要确认"));
        assertTrue(response.getContent().contains("目标平台"));
    }

    @Test
    void clarifyingStageCanReturnToUnderstandingConfirmedAfterSupplement() {
        Conversation conversation = new Conversation();
        conversation.setId(6L);
        conversation.setProjectName("新对话");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.CLARIFYING);
        conversation.setUserRequirement("做一个春节祝福和排行榜小程序");

        when(conversationRepository.findById(6L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(promptTaskService.understandRequirement(any(String.class))).thenReturn(
                "<REQUIREMENT_GATE>\n" +
                "NEXT_ACTION: READY_FOR_CONFIRM\n" +
                "MISSING_INFO_COUNT: 0\n" +
                "</REQUIREMENT_GATE>\n\n" +
                "已完成需求理解。"
        );

        MessageDTO userMessage = new MessageDTO();
        userMessage.setContent("平台是微信小程序，排行榜按收到祝福数量排名。");

        conversationService.sendMessageToNewConversation(6L, userMessage);

        assertEquals(ConversationStage.UNDERSTANDING_CONFIRMED, conversation.getStage());
        assertTrue(conversation.getUserRequirement().contains("做一个春节祝福和排行榜小程序"));
        assertTrue(conversation.getUserRequirement().contains("平台是微信小程序"));
    }

    @Test
    void returnsCurrentQuestionIndexAndAnsweredQuestionIdsInClarifyingStage() {
        Conversation conversation = new Conversation();
        conversation.setId(7L);
        conversation.setProjectName("新对话");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.CLARIFYING);
        conversation.setAiUnderstanding(
                "<REQUIREMENT_GATE>\n" +
                        "NEXT_ACTION: ASK_CLARIFICATION\n" +
                        "MISSING_INFO_COUNT: 2\n" +
                        "</REQUIREMENT_GATE>\n\n" +
                        "<CLARIFICATION_PAYLOAD>\n" +
                        "{\n" +
                        "  \"questions\": [\n" +
                        "    {\"id\":\"platform\",\"question\":\"请选择目标平台\",\"options\":[\"微信小程序\",\"H5\"]},\n" +
                        "    {\"id\":\"ranking\",\"question\":\"请选择排行榜规则\",\"options\":[\"按收到量\",\"按发送量\"]}\n" +
                        "  ]\n" +
                        "}\n" +
                        "</CLARIFICATION_PAYLOAD>"
        );

        Message answeredMessage = new Message();
        answeredMessage.setId(100L);
        answeredMessage.setConversationId(7L);
        answeredMessage.setRole(Message.MessageRole.USER);
        answeredMessage.setSenderName("用户");
        answeredMessage.setContent("澄清回答：请选择目标平台\n答案：微信小程序");

        when(conversationRepository.findById(7L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(answeredMessage));

        ConversationDTO dto = conversationService.getNewConversationById(7L);

        assertEquals(1, dto.getCurrentQuestionIndex());
        assertEquals(Set.of("platform"), Set.copyOf(dto.getAnsweredQuestionIds()));
    }
}
