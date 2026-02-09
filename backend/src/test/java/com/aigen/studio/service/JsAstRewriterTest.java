package com.aigen.studio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JsAstRewriterTest {

    @Test
    void splitsDeclarationsAndMountedBody() {
        JsAstRewriter rewriter = new JsAstRewriter();
        String script = """
                const questions = [];
                function startQuiz() { console.log('start'); }
                init();
                """;

        JsAstRewriteResult result = rewriter.rewrite(script);

        assertTrue(result.topLevel().contains("function startQuiz"));
        assertTrue(result.topLevel().contains("const questions"));
        assertTrue(result.mountedBody().contains("init()"));
    }

    @Test
    void supportsArrowFunctions() {
        JsAstRewriter rewriter = new JsAstRewriter();
        String script = """
                let count = 0;
                const init = () => {
                  const list = [1, 2];
                  list.forEach((x) => console.log(x));
                };
                init();
                """;

        JsAstRewriteResult result = rewriter.rewrite(script);

        assertTrue(result.topLevel().contains("let count"));
        assertTrue(result.topLevel().contains("const init"));
        assertTrue(result.mountedBody().contains("init()"));
    }
}
