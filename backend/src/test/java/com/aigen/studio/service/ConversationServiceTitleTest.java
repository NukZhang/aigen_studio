package com.aigen.studio.service;

import com.aigen.studio.dto.ConversationDTO;
import com.aigen.studio.dto.MessageDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.config.AgentProperties;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import com.aigen.studio.rag.ConversationVectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTitleTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UnderstandingService understandingService;

    @Mock
    private CodeGenerationService codeGenerationService;

    @Mock
    private UIPrototypeService uiPrototypeService;

    @Mock
    private PreviewService previewService;

    @Mock
    private SdacResourceService sdacResourceService;

    @Mock
    private ConversationVectorService conversationVectorService;

    @Mock
    private AgentProperties agentProperties;

    @Mock
    private Executor taskExecutor;

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
        MessageDTO userMessage = new MessageDTO();
        userMessage.setContent("我想做一个库存管理系统，支持商品管理和库存预警");

        MessageDTO response = conversationService.sendMessageToNewConversation(1L, userMessage);

        assertEquals("新对话", conversation.getProjectName());
        assertEquals(ConversationStage.UNDERSTANDING, conversation.getStage());
        assertTrue(response.getContent().contains("后台理解"));
        verify(understandingService, never()).understandRequirement(any(String.class), any(Long.class), any());
        verify(taskExecutor, atLeastOnce()).execute(any(Runnable.class));
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
        MessageDTO userMessage = new MessageDTO();
        userMessage.setContent("做一个春节祝福和排行榜小程序");

        MessageDTO response = conversationService.sendMessageToNewConversation(5L, userMessage);

        assertEquals(ConversationStage.UNDERSTANDING, conversation.getStage());
        assertTrue(response.getContent().contains("后台理解"));
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
        MessageDTO userMessage = new MessageDTO();
        userMessage.setContent("平台是微信小程序，排行榜按收到祝福数量排名。");

        MessageDTO response = conversationService.sendMessageToNewConversation(6L, userMessage);

        assertEquals(ConversationStage.UNDERSTANDING, conversation.getStage());
        assertTrue(conversation.getUserRequirement().contains("做一个春节祝福和排行榜小程序"));
        assertTrue(conversation.getUserRequirement().contains("平台是微信小程序"));
        assertTrue(response.getContent().contains("后台重新理解"));
        verify(understandingService, never()).understandRequirement(any(String.class), any(Long.class), any());
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

    @Test
    void resolvesAllAnsweredQuestionIdsFromSingleBatchClarificationMessage() {
        Conversation conversation = new Conversation();
        conversation.setId(8L);
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
        answeredMessage.setId(101L);
        answeredMessage.setConversationId(8L);
        answeredMessage.setRole(Message.MessageRole.USER);
        answeredMessage.setSenderName("用户");
        answeredMessage.setContent(
                "澄清回答：请选择目标平台\n答案：微信小程序\n" +
                        "澄清回答：请选择排行榜规则\n答案：按收到量"
        );

        when(conversationRepository.findById(8L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(8L)).thenReturn(List.of(answeredMessage));

        ConversationDTO dto = conversationService.getNewConversationById(8L);

        assertEquals(Set.of("platform", "ranking"), Set.copyOf(dto.getAnsweredQuestionIds()));
        assertEquals(null, dto.getCurrentQuestionIndex());
    }

    @Test
    void reUnderstandCommandTriggersConversationVectorIndexing() {
        Conversation conversation = new Conversation();
        conversation.setId(9L);
        conversation.setProjectName("新对话");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UI_READY);
        conversation.setUserRequirement("实现任务管理系统");

        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentProperties.isUseLangChain()).thenReturn(true);
        when(agentProperties.isEnableRag()).thenReturn(true);
        when(understandingService.understandRequirement("实现任务管理系统", 9L)).thenReturn("理解结果");
        when(conversationVectorService.indexConversationAsync(9L))
                .thenReturn(CompletableFuture.completedFuture(new ConversationVectorService.VectorizationResult(1, 1)));

        MessageDTO message = new MessageDTO();
        message.setContent("重新理解需求");

        MessageDTO response = conversationService.sendMessageToNewConversation(9L, message);

        assertTrue(response.getContent().contains("重新理解"));
        verify(conversationVectorService).indexConversationAsync(9L);
    }
}
