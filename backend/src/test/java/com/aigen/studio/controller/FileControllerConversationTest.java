package com.aigen.studio.controller;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:file-controller-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "iflow.sdk.output-dir=target/file-controller-generated"
})
@AutoConfigureMockMvc
class FileControllerConversationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Conversation createConversation(Path root) {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.SERVICE_STARTING);
        conversation.setGeneratedCodePath(root.toString());
        return conversationRepository.save(conversation);
    }

    @Test
    void getsConversationFileTree(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "hi");
        Conversation conversation = createConversation(tmp);

        mockMvc.perform(get("/files/conversation/{id}/tree", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].children[0].name", is("a.txt")));
    }

    @Test
    void getsConversationFileTreeWithNestedDirectory(@TempDir Path tmp) throws Exception {
        Path srcDir = Files.createDirectories(tmp.resolve("src"));
        Files.writeString(srcDir.resolve("main.txt"), "nested");
        Conversation conversation = createConversation(tmp);

        mockMvc.perform(get("/files/conversation/{id}/tree", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].children[0].name", is("src")))
                .andExpect(jsonPath("$[0].children[0].children[0].name", is("main.txt")));
    }

    @Test
    void getsConversationFileTreeWhenPathNotReady() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.SERVICE_STARTING);
        conversation.setGeneratedCodePath(null);
        conversation = conversationRepository.save(conversation);

        mockMvc.perform(get("/files/conversation/{id}/tree", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void readsConversationFileContent(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "hello");
        Conversation conversation = createConversation(tmp);

        mockMvc.perform(get("/files/conversation/{id}/content", conversation.getId())
                        .param("filePath", "a.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.content", is("hello")));
    }

    @Test
    void readsFromConfiguredOutputWhenStoredPathPointsToBackendDirectory() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.SERVICE_STARTING);
        conversation = conversationRepository.save(conversation);

        Path configuredRoot = Path.of("target", "file-controller-generated", "conversation-" + conversation.getId())
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(configuredRoot);
        Files.writeString(configuredRoot.resolve("a.txt"), "from-configured-root");

        conversation.setGeneratedCodePath(Path.of("backend").toAbsolutePath().normalize().toString());
        conversationRepository.save(conversation);

        mockMvc.perform(get("/files/conversation/{id}/content", conversation.getId())
                        .param("filePath", "a.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.content", is("from-configured-root")));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(configuredRoot.toString(), Path.of(updated.getGeneratedCodePath()).toAbsolutePath().normalize().toString());
    }

    @Test
    void writesConversationFileContent(@TempDir Path tmp) throws Exception {
        Conversation conversation = createConversation(tmp);

        mockMvc.perform(put("/files/conversation/{id}/content", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "filePath", "a.txt",
                                "content", "saved"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertEquals("saved", Files.readString(tmp.resolve("a.txt")));
    }

    @Test
    void createsConversationFile(@TempDir Path tmp) throws Exception {
        Conversation conversation = createConversation(tmp);

        mockMvc.perform(post("/files/conversation/{id}/create", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("filePath", "dir/new.txt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertTrue(Files.exists(tmp.resolve("dir/new.txt")));
    }

    @Test
    void createsConversationDirectory(@TempDir Path tmp) throws Exception {
        Conversation conversation = createConversation(tmp);

        mockMvc.perform(post("/files/conversation/{id}/mkdir", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("path", "dir/sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertTrue(Files.isDirectory(tmp.resolve("dir/sub")));
    }

    @Test
    void renamesConversationPath(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("old.txt"), "content");
        Conversation conversation = createConversation(tmp);

        mockMvc.perform(post("/files/conversation/{id}/rename", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "from", "old.txt",
                                "to", "new.txt"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertTrue(Files.exists(tmp.resolve("new.txt")));
    }

    @Test
    void deletesConversationPath(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("gone.txt"), "content");
        Conversation conversation = createConversation(tmp);

        mockMvc.perform(post("/files/conversation/{id}/delete", conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("path", "gone.txt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertFalse(Files.exists(tmp.resolve("gone.txt")));
    }

    @Test
    void uploadsConversationFile(@TempDir Path tmp) throws Exception {
        Conversation conversation = createConversation(tmp);
        MockMultipartFile file = new MockMultipartFile("file", "u.txt", "text/plain", "hi".getBytes());

        mockMvc.perform(multipart("/files/conversation/{id}/upload", conversation.getId())
                        .file(file)
                        .param("targetDir", "dir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertEquals("hi", Files.readString(tmp.resolve("dir/u.txt")));
    }
}
