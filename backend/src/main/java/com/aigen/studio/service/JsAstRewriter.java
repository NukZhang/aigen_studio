package com.aigen.studio.service;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.VariableDeclaration;

public class JsAstRewriter {

    public JsAstRewriteResult rewrite(String script) {
        if (script == null || script.isBlank()) {
            return new JsAstRewriteResult("", "");
        }
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        Parser parser = new Parser(env);
        AstRoot root = parser.parse(script, null, 1);

        StringBuilder topLevel = new StringBuilder();
        StringBuilder mounted = new StringBuilder();

        for (Node node : root) {
            if (!(node instanceof AstNode astNode)) {
                continue;
            }
            if (astNode instanceof FunctionNode || astNode instanceof VariableDeclaration) {
                topLevel.append(astNode.toSource()).append("\n");
            } else {
                mounted.append(astNode.toSource()).append("\n");
            }
        }

        return new JsAstRewriteResult(topLevel.toString().trim(), mounted.toString().trim());
    }
}
