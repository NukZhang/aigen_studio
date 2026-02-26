package com.aigen.studio.rag;

import dev.langchain4j.data.segment.TextSegment;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStoreService vectorStoreService;

    @InjectMocks
    private DocumentIngestionService documentIngestionService;

    @Test
    void ingestMarkdownNormalizesTextAndPropagatesMetadata() {
        when(vectorStoreService.addSegments(anyList())).thenReturn(List.of("seg-1"));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "spec");

        DocumentIngestionService.IngestionResult result = documentIngestionService.ingestMarkdown(
                "# 标题\n\n- 功能一\n- 功能二\n\n**加粗说明**",
                metadata
        );

        assertEquals(1, result.segmentIds().size());

        ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStoreService).addSegments(captor.capture());
        List<TextSegment> segments = captor.getValue();
        assertFalse(segments.isEmpty());

        String merged = segments.stream().map(TextSegment::text).reduce("", (a, b) -> a + b);
        assertTrue(merged.contains("标题"));
        assertTrue(merged.contains("功能一"));
        assertFalse(merged.contains("#"));
        assertFalse(merged.contains("**"));
        assertEquals("spec", segments.get(0).metadata().get("source"));
    }

    @Test
    void ingestPdfExtractsTextAndStoresSegments() throws Exception {
        when(vectorStoreService.addSegments(anyList())).thenReturn(List.of("pdf-seg-1"));

        byte[] pdfBytes = createSimplePdf("Qdrant semantic retrieval");

        DocumentIngestionService.IngestionResult result = documentIngestionService.ingestPdf(
                pdfBytes,
                Map.of("fileName", "knowledge.pdf")
        );

        assertEquals(1, result.segmentIds().size());

        ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStoreService).addSegments(captor.capture());
        List<TextSegment> segments = captor.getValue();
        assertFalse(segments.isEmpty());
        assertTrue(segments.get(0).text().contains("Qdrant"));
        assertEquals("knowledge.pdf", segments.get(0).metadata().get("fileName"));
    }

    private byte[] createSimplePdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
