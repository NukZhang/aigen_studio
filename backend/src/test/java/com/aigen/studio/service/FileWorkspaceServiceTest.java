package com.aigen.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileWorkspaceServiceTest {

    @Test
    void rejectsPathTraversal(@TempDir Path tmp) {
        FileWorkspaceService service = new FileWorkspaceService();
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveSafePath(tmp, "../evil.txt"));
    }

    @Test
    void writesAndReadsText(@TempDir Path tmp) {
        FileWorkspaceService service = new FileWorkspaceService();
        service.writeText(tmp, "a.txt", "hello");
        String content = service.readText(tmp, "a.txt");
        assertEquals("hello", content);
    }

    @Test
    void createsAndDeletesFile(@TempDir Path tmp) {
        FileWorkspaceService service = new FileWorkspaceService();
        service.createFile(tmp, "dir/new.txt");
        assertTrue(Files.exists(tmp.resolve("dir/new.txt")));
        service.deletePath(tmp, "dir/new.txt");
        assertFalse(Files.exists(tmp.resolve("dir/new.txt")));
    }

    @Test
    void renamesFile(@TempDir Path tmp) {
        FileWorkspaceService service = new FileWorkspaceService();
        service.createFile(tmp, "a.txt");
        service.renamePath(tmp, "a.txt", "b.txt");
        assertTrue(Files.exists(tmp.resolve("b.txt")));
    }

    @Test
    void createsDirectory(@TempDir Path tmp) {
        FileWorkspaceService service = new FileWorkspaceService();
        service.createDirectory(tmp, "dir/sub");
        assertTrue(Files.isDirectory(tmp.resolve("dir/sub")));
    }

    @Test
    void uploadsFile(@TempDir Path tmp) throws Exception {
        FileWorkspaceService service = new FileWorkspaceService();
        MockMultipartFile file = new MockMultipartFile("file", "u.txt", "text/plain", "hi".getBytes());
        service.saveUpload(tmp, "dir", file);
        assertEquals("hi", Files.readString(tmp.resolve("dir/u.txt")));
    }
}
