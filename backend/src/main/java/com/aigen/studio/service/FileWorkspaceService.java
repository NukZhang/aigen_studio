package com.aigen.studio.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Service
public class FileWorkspaceService {

    public Path resolveSafePath(Path root, String relativePath) {
        Path normalized = root.resolve(relativePath).normalize();
        if (!normalized.startsWith(root.normalize())) {
            throw new IllegalArgumentException("Invalid path");
        }
        return normalized;
    }

    public String readText(Path root, String relativePath) {
        Path target = resolveSafePath(root, relativePath);
        try {
            return Files.readString(target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeText(Path root, String relativePath, String content) {
        Path target = resolveSafePath(root, relativePath);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createFile(Path root, String relativePath) {
        Path target = resolveSafePath(root, relativePath);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.createFile(target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createDirectory(Path root, String relativePath) {
        Path target = resolveSafePath(root, relativePath);
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void renamePath(Path root, String from, String to) {
        Path source = resolveSafePath(root, from);
        Path target = resolveSafePath(root, to);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.move(source, target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deletePath(Path root, String relativePath) {
        Path target = resolveSafePath(root, relativePath);
        try {
            if (Files.isDirectory(target)) {
                try (var walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Ignore individual delete failures to continue cleanup
                        }
                    });
                }
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveUpload(Path root, String targetDir, MultipartFile file) {
        Path dir = resolveSafePath(root, targetDir == null ? "" : targetDir);
        try {
            Files.createDirectories(dir);
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException("Invalid filename");
            }
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(root.normalize())) {
                throw new IllegalArgumentException("Invalid path");
            }
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
