package com.aigen.studio.service;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeGenerationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generateCodeInjectsUiPrototypeIntoVueApp() throws Exception {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        PromptTaskService promptTaskService = mock(PromptTaskService.class);
        PreviewScriptService previewScriptService = mock(PreviewScriptService.class);
        UIPrototypeService uiPrototypeService = mock(UIPrototypeService.class);
        FrontendScaffoldService frontendScaffoldService = new FrontendScaffoldService();

        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setProjectName("Demo Project");
        conversation.setUserRequirement("Need UI");
        conversation.setAiUnderstanding("Understood");
        conversation.setStage(ConversationStage.UI_CONFIRMED);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(uiPrototypeService.getUIPrototype(1L)).thenReturn(
                "<html><head><style>.card{color:red;}</style></head>" +
                "<body><div class=\"card\">Hello</div></body></html>"
        );

        doAnswer(invocation -> {
            Path outputPath = invocation.getArgument(1);
            Files.createDirectories(outputPath.resolve("frontend"));
            return null;
        }).when(promptTaskService).generateCode(anyString(), any(Path.class), any());

        CodeGenerationService service = new CodeGenerationService(
                conversationRepository,
                messageRepository,
                promptTaskService,
                previewScriptService,
                uiPrototypeService,
                frontendScaffoldService
        );
        ReflectionTestUtils.setField(service, "outputDir", tempDir.toString());

        service.generateCodeForConversationAsync(1L);

        Path frontendDir = tempDir.resolve("conversation-1").resolve("frontend");
        Path appVue = frontendDir.resolve("src").resolve("App.vue");
        Path indexHtml = frontendDir.resolve("index.html");

        assertTrue(Files.exists(appVue));
        assertTrue(Files.exists(indexHtml));

        String appContent = Files.readString(appVue);
        String indexContent = Files.readString(indexHtml);

        assertTrue(appContent.contains("class=\"card\""));
        assertTrue(appContent.contains(".card{color:red;}") || appContent.contains(".card { color: red; }"));
        assertTrue(indexContent.contains("id=\"app\""));
        assertTrue(indexContent.contains("/src/main.ts"));
    }
}
