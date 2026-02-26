package com.aigen.studio.service;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        BackendGenerationFixer backendGenerationFixer = new BackendGenerationFixer();

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
            Path apiDir = outputPath.resolve("frontend/src/api");
            Files.createDirectories(apiDir);
            Files.writeString(apiDir.resolve("demo.ts"), """
                    export async function ping() {
                      return fetch('/api/ping')
                    }
                    """);
            return null;
        }).when(promptTaskService).generateCode(anyString(), any(Path.class), any());

        CodeGenerationService service = new CodeGenerationService(
                conversationRepository,
                messageRepository,
                promptTaskService,
                previewScriptService,
                uiPrototypeService,
                frontendScaffoldService,
                backendGenerationFixer
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

    @Test
    void generateCodeKeepsAiGeneratedAppVueAndPassesBackendIntegrationCheck() throws Exception {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        PromptTaskService promptTaskService = mock(PromptTaskService.class);
        PreviewScriptService previewScriptService = mock(PreviewScriptService.class);
        UIPrototypeService uiPrototypeService = mock(UIPrototypeService.class);
        FrontendScaffoldService frontendScaffoldService = new FrontendScaffoldService();
        BackendGenerationFixer backendGenerationFixer = new BackendGenerationFixer();

        Conversation conversation = new Conversation();
        conversation.setId(2L);
        conversation.setProjectName("Demo Project");
        conversation.setUserRequirement("Need full stack");
        conversation.setAiUnderstanding("Understood");
        conversation.setStage(ConversationStage.UI_CONFIRMED);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);

        when(conversationRepository.findById(2L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(uiPrototypeService.getUIPrototype(2L)).thenReturn("<html><body><div class=\"prototype\">UI</div></body></html>");

        doAnswer(invocation -> {
            Path outputPath = invocation.getArgument(1);
            Path srcDir = outputPath.resolve("frontend/src");
            Path apiDir = srcDir.resolve("api");
            Path viewsDir = srcDir.resolve("views");
            Files.createDirectories(apiDir);
            Files.createDirectories(viewsDir);

            Files.writeString(srcDir.resolve("main.ts"), """
                    import { createApp } from 'vue'
                    import { createRouter, createWebHistory } from 'vue-router'
                    import App from './App.vue'
                    import HomeView from './views/HomeView.vue'

                    const router = createRouter({
                      history: createWebHistory(import.meta.env.BASE_URL),
                      routes: [{ path: '/', component: HomeView }]
                    })

                    const app = createApp(App)
                    app.use(router)
                    app.mount('#app')
                    """);
            Files.writeString(srcDir.resolve("App.vue"), """
                    <template>
                      <router-view />
                    </template>
                    """);
            Files.writeString(apiDir.resolve("http.ts"), """
                    import axios from 'axios'
                    export const http = axios.create({ baseURL: '/api', timeout: 10000 })
                    """);
            Files.writeString(viewsDir.resolve("HomeView.vue"), """
                    <script setup lang="ts">
                    import { onMounted } from 'vue'
                    import { http } from '../api/http'

                    onMounted(() => {
                      http.get('/health')
                    })
                    </script>

                    <template>
                      <div>home</div>
                    </template>
                    """);
            return null;
        }).when(promptTaskService).generateCode(anyString(), any(Path.class), any());

        CodeGenerationService service = new CodeGenerationService(
                conversationRepository,
                messageRepository,
                promptTaskService,
                previewScriptService,
                uiPrototypeService,
                frontendScaffoldService,
                backendGenerationFixer
        );
        ReflectionTestUtils.setField(service, "outputDir", tempDir.toString());

        service.generateCodeForConversationAsync(2L);

        Path appVue = tempDir.resolve("conversation-2/frontend/src/App.vue");
        String appContent = Files.readString(appVue);

        assertTrue(appContent.contains("<router-view"));
        assertFalse(appContent.contains("prototype"));
        assertEquals(ConversationStage.READY_TO_START, conversation.getStage());
    }

    @Test
    void generateCodeFailsWhenRouterAppVueCannotReachBackendPages() throws Exception {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        PromptTaskService promptTaskService = mock(PromptTaskService.class);
        PreviewScriptService previewScriptService = mock(PreviewScriptService.class);
        UIPrototypeService uiPrototypeService = mock(UIPrototypeService.class);
        FrontendScaffoldService frontendScaffoldService = new FrontendScaffoldService();
        BackendGenerationFixer backendGenerationFixer = new BackendGenerationFixer();

        Conversation conversation = new Conversation();
        conversation.setId(3L);
        conversation.setProjectName("Demo Project");
        conversation.setUserRequirement("Need full stack");
        conversation.setAiUnderstanding("Understood");
        conversation.setStage(ConversationStage.UI_CONFIRMED);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);

        when(conversationRepository.findById(3L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(uiPrototypeService.getUIPrototype(3L)).thenReturn("<html><body><div>UI</div></body></html>");

        doAnswer(invocation -> {
            Path outputPath = invocation.getArgument(1);
            Path srcDir = outputPath.resolve("frontend/src");
            Path apiDir = srcDir.resolve("api");
            Files.createDirectories(apiDir);

            Files.writeString(srcDir.resolve("main.ts"), """
                    import { createApp } from 'vue'
                    import { createRouter, createWebHistory } from 'vue-router'
                    import App from './App.vue'

                    const router = createRouter({
                      history: createWebHistory(import.meta.env.BASE_URL),
                      routes: []
                    })

                    const app = createApp(App)
                    app.use(router)
                    app.mount('#app')
                    """);
            Files.writeString(srcDir.resolve("App.vue"), """
                    <template>
                      <div>static page</div>
                    </template>
                    """);
            Files.writeString(apiDir.resolve("http.ts"), """
                    import axios from 'axios'
                    export const http = axios.create({ baseURL: '/api' })
                    """);
            return null;
        }).when(promptTaskService).generateCode(anyString(), any(Path.class), any());

        CodeGenerationService service = new CodeGenerationService(
                conversationRepository,
                messageRepository,
                promptTaskService,
                previewScriptService,
                uiPrototypeService,
                frontendScaffoldService,
                backendGenerationFixer
        );
        ReflectionTestUtils.setField(service, "outputDir", tempDir.toString());

        service.generateCodeForConversationAsync(3L);

        assertEquals(ConversationStage.FAILED, conversation.getStage());
        assertTrue(conversation.getErrorMessage().contains("router-view"));
    }

    @Test
    void generateCodeAcceptsPascalCaseRouterViewInAppVue() throws Exception {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        PromptTaskService promptTaskService = mock(PromptTaskService.class);
        PreviewScriptService previewScriptService = mock(PreviewScriptService.class);
        UIPrototypeService uiPrototypeService = mock(UIPrototypeService.class);
        FrontendScaffoldService frontendScaffoldService = new FrontendScaffoldService();
        BackendGenerationFixer backendGenerationFixer = new BackendGenerationFixer();

        Conversation conversation = new Conversation();
        conversation.setId(5L);
        conversation.setProjectName("Demo Project");
        conversation.setUserRequirement("Need full stack");
        conversation.setAiUnderstanding("Understood");
        conversation.setStage(ConversationStage.UI_CONFIRMED);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);

        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(uiPrototypeService.getUIPrototype(5L)).thenReturn("<html><body><div>UI</div></body></html>");

        doAnswer(invocation -> {
            Path outputPath = invocation.getArgument(1);
            Path srcDir = outputPath.resolve("frontend/src");
            Path apiDir = srcDir.resolve("api");
            Files.createDirectories(apiDir);

            Files.writeString(srcDir.resolve("main.ts"), """
                    import { createApp } from 'vue'
                    import App from './App.vue'
                    import router from './router'

                    const app = createApp(App)
                    app.use(router)
                    app.mount('#app')
                    """);
            Files.writeString(srcDir.resolve("App.vue"), """
                    <script setup lang="ts">
                    import { RouterView } from 'vue-router'
                    </script>

                    <template>
                      <RouterView />
                    </template>
                    """);
            Files.writeString(srcDir.resolve("router.ts"), """
                    import { createRouter, createWebHistory } from 'vue-router'
                    export default createRouter({
                      history: createWebHistory(import.meta.env.BASE_URL),
                      routes: []
                    })
                    """);
            Files.writeString(apiDir.resolve("http.ts"), """
                    import axios from 'axios'
                    export const http = axios.create({ baseURL: '/api' })
                    """);
            return null;
        }).when(promptTaskService).generateCode(anyString(), any(Path.class), any());

        CodeGenerationService service = new CodeGenerationService(
                conversationRepository,
                messageRepository,
                promptTaskService,
                previewScriptService,
                uiPrototypeService,
                frontendScaffoldService,
                backendGenerationFixer
        );
        ReflectionTestUtils.setField(service, "outputDir", tempDir.toString());

        service.generateCodeForConversationAsync(5L);

        assertEquals(ConversationStage.READY_TO_START, conversation.getStage());
    }

    @Test
    void generateCodeSendsHeartbeatWhenGenerationSilenceTooLong() throws Exception {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        PromptTaskService promptTaskService = mock(PromptTaskService.class);
        PreviewScriptService previewScriptService = mock(PreviewScriptService.class);
        UIPrototypeService uiPrototypeService = mock(UIPrototypeService.class);
        FrontendScaffoldService frontendScaffoldService = new FrontendScaffoldService();
        BackendGenerationFixer backendGenerationFixer = new BackendGenerationFixer();

        Conversation conversation = new Conversation();
        conversation.setId(4L);
        conversation.setProjectName("Heartbeat Project");
        conversation.setUserRequirement("Need full stack");
        conversation.setAiUnderstanding("Understood");
        conversation.setStage(ConversationStage.CODE_GENERATING);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);

        when(conversationRepository.findById(4L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(uiPrototypeService.getUIPrototype(4L)).thenReturn("<html><body><div>UI</div></body></html>");

        doAnswer(invocation -> {
            Path outputPath = invocation.getArgument(1);
            Path srcDir = outputPath.resolve("frontend/src");
            Path apiDir = srcDir.resolve("api");
            Files.createDirectories(apiDir);
            Files.writeString(srcDir.resolve("main.ts"), """
                    import { createApp } from 'vue'
                    import { createRouter, createWebHistory } from 'vue-router'
                    import App from './App.vue'
                    const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes: [] })
                    const app = createApp(App)
                    app.use(router)
                    app.mount('#app')
                    """);
            Files.writeString(srcDir.resolve("App.vue"), """
                    <template>
                      <router-view />
                    </template>
                    """);
            Files.writeString(apiDir.resolve("http.ts"), """
                    import axios from 'axios'
                    export const http = axios.create({ baseURL: '/api', timeout: 10000 })
                    """);

            Thread.sleep(220L);
            return null;
        }).when(promptTaskService).generateCode(anyString(), any(Path.class), any());

        CodeGenerationService service = new CodeGenerationService(
                conversationRepository,
                messageRepository,
                promptTaskService,
                previewScriptService,
                uiPrototypeService,
                frontendScaffoldService,
                backendGenerationFixer
        );
        ReflectionTestUtils.setField(service, "outputDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "codeHeartbeatIntervalMillis", 50L);
        ReflectionTestUtils.setField(service, "codeHeartbeatInitialDelayMillis", 10L);

        service.generateCodeForConversationAsync(4L);

        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, atLeast(1)).save(captor.capture());
        List<Message> messages = captor.getAllValues();

        assertTrue(
                messages.stream().anyMatch(m -> m.getContent() != null && m.getContent().contains("代码生成中")),
                "Should emit heartbeat message during long code generation silence"
        );
        assertEquals(ConversationStage.READY_TO_START, conversation.getStage());
    }
}
