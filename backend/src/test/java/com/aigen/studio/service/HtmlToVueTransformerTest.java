package com.aigen.studio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlToVueTransformerTest {

    @Test
    void convertsInlineEventsAndRemovesScriptTags() {
        JsAstRewriter rewriter = new JsAstRewriter();
        HtmlToVueTransformer transformer = new HtmlToVueTransformer(rewriter);

        String html = """
                <html>
                  <head><style>.x{color:red;}</style></head>
                  <body>
                    <button onclick=\"startQuiz()\">Start</button>
                    <input oninput=\"handleInput()\" />
                    <script>function startQuiz(){} function handleInput(){}</script>
                  </body>
                </html>
                """;

        VueSfcParts parts = transformer.transform(html);

        assertTrue(parts.template().contains("@click=\"startQuiz()\""));
        assertTrue(parts.template().contains("@input=\"handleInput()\""));
        assertFalse(parts.template().contains("<script"));
        assertFalse(parts.template().contains("<style"));
        assertTrue(parts.scriptSetup().contains("function startQuiz"));
    }
}
