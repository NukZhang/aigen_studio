package com.aigen.studio.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HtmlToVueTransformer {

    private final JsAstRewriter jsAstRewriter;

    public HtmlToVueTransformer(JsAstRewriter jsAstRewriter) {
        this.jsAstRewriter = jsAstRewriter;
    }

    public VueSfcParts transform(String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        Element body = doc.body();
        if (body == null) {
            return new VueSfcParts("", "", "");
        }

        StringBuilder scriptContent = new StringBuilder();
        Elements scripts = body.getElementsByTag("script");
        for (Element script : scripts) {
            scriptContent.append(script.data()).append("\n");
        }
        scripts.remove();

        Elements styles = doc.getElementsByTag("style");
        StringBuilder style = new StringBuilder();
        for (Element styleTag : styles) {
            style.append(styleTag.data()).append("\n");
        }
        styles.remove();

        for (Element el : body.getAllElements()) {
            List<org.jsoup.nodes.Attribute> attributes = new ArrayList<>(el.attributes().asList());
            for (org.jsoup.nodes.Attribute attr : attributes) {
                String key = attr.getKey();
                if (key.toLowerCase(Locale.ROOT).startsWith("on")) {
                    String event = key.substring(2);
                    el.removeAttr(key);
                    el.attr("@" + event, attr.getValue());
                }
            }
        }

        String template = body.html();
        JsAstRewriteResult rewritten;
        try {
            rewritten = jsAstRewriter.rewrite(scriptContent.toString());
        } catch (Exception e) {
            rewritten = new JsAstRewriteResult("", "");
        }

        String scriptSetup = buildScriptSetup(rewritten);

        return new VueSfcParts(template, scriptSetup, style.toString().trim());
    }

    private String buildScriptSetup(JsAstRewriteResult rewritten) {
        StringBuilder sb = new StringBuilder();
        boolean hasMounted = rewritten.mountedBody() != null && !rewritten.mountedBody().isBlank();
        if (hasMounted) {
            sb.append("import { onMounted } from 'vue'\n\n");
        }
        if (rewritten.topLevel() != null && !rewritten.topLevel().isBlank()) {
            sb.append(rewritten.topLevel()).append("\n\n");
        }
        if (hasMounted) {
            sb.append("onMounted(() => {\n")
              .append(rewritten.mountedBody())
              .append("\n});\n");
        }
        return sb.toString().trim();
    }
}
