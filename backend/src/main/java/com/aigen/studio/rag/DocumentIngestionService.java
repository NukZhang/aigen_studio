package com.aigen.studio.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStoreService vectorStoreService;
    private final RAGService ragService;
    private final DocumentSplitter documentSplitter = new DocumentByParagraphSplitter(500, 100);

    public IngestionResult ingestMultipartFile(MultipartFile file, Map<String, Object> metadata) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        Map<String, Object> merged = new HashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.putIfAbsent("fileName", file.getOriginalFilename());
        merged.putIfAbsent("contentType", file.getContentType());

        try {
            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            if (contentType.contains("pdf") || filename.endsWith(".pdf")) {
                return ingestPdf(file.getBytes(), merged);
            }
            return ingestMarkdown(new String(file.getBytes(), StandardCharsets.UTF_8), merged);
        } catch (IOException e) {
            throw new RuntimeException("Failed to ingest file: " + file.getOriginalFilename(), e);
        }
    }

    public IngestionResult ingestMarkdown(String markdown, Map<String, Object> metadata) {
        String normalized = normalizeMarkdown(markdown);
        return ingestText(normalized, metadata);
    }

    public IngestionResult ingestPdf(byte[] pdfBytes, Map<String, Object> metadata) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF bytes are empty");
        }

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            String extracted = new PDFTextStripper().getText(document);
            return ingestText(extracted, metadata);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse PDF content", e);
        }
    }

    public IngestionResult ingestText(String text, Map<String, Object> metadata) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Document text is empty");
        }

        Metadata ingestMetadata = toMetadata(metadata);
        Document document = Document.from(text.trim());
        List<TextSegment> segments = documentSplitter.split(document).stream()
                .map(segment -> TextSegment.from(segment.text(), mergeMetadata(segment.metadata(), ingestMetadata)))
                .collect(Collectors.toList());

        List<String> segmentIds = vectorStoreService.addSegments(segments);
        ragService.invalidateSearchCache();
        log.info("Ingested {} segments into vector store", segmentIds.size());
        return new IngestionResult(segmentIds.size(), segmentIds);
    }

    private Metadata toMetadata(Map<String, Object> metadata) {
        Metadata target = new Metadata();
        if (metadata == null || metadata.isEmpty()) {
            return target;
        }
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                target.put(key, String.valueOf(value));
            }
        });
        return target;
    }

    private Metadata mergeMetadata(Metadata original, Metadata extra) {
        Metadata merged = original == null ? new Metadata() : original.copy();
        if (extra == null) {
            return merged;
        }
        extra.asMap().forEach(merged::put);
        return merged;
    }

    private String normalizeMarkdown(String markdown) {
        if (markdown == null) {
            return "";
        }
        return markdown
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("`{1,3}", "")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("\\[(.*?)]\\((.*?)\\)", "$1")
                .replaceAll("\\r\\n?", "\n")
                .trim();
    }

    public record IngestionResult(int segmentCount, List<String> segmentIds) {
    }
}
