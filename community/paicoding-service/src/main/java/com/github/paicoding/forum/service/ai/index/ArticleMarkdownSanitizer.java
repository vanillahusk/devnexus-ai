package com.github.paicoding.forum.service.ai.index;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面向知识索引的保守 Markdown 清洗器。代码围栏内不做任何内容替换，避免损坏示例代码。
 */
@Component
public class ArticleMarkdownSanitizer {
    private static final Pattern IMAGE = Pattern.compile("!\\[([^]]*)]\\([^)]*\\)");
    private static final Pattern NAVIGATION_LINK = Pattern.compile(
            "^\\s*\\[(?:返回顶部|上一篇[^]]*|下一篇[^]]*)]\\([^)]*\\)\\s*$");
    private static final Pattern ADVERTISEMENT_LINK = Pattern.compile(
            "^\\s*(?:>\\s*)?(?:广告|推广)[:：]\\s*\\[[^]]+]\\([^)]*\\)\\s*$");
    private static final Pattern SAFE_HTML_TAG = Pattern.compile(
            "</?(?:div|span|p|section|article|header|footer|nav|aside|strong|em|b|i|u|small|mark)(?:\\s+[^>]*)?>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BREAK_TAG = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);

    public String sanitize(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<String> cleaned = new ArrayList<>(lines.length);
        boolean inFence = false;
        boolean suppressHtmlBlock = false;
        String suppressedTag = null;
        boolean suppressAdvertisement = false;
        for (String line : lines) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                cleaned.add(line);
                continue;
            }
            if (inFence) {
                cleaned.add(line);
                continue;
            }
            if (suppressAdvertisement) {
                if (trimmed.contains("<!-- ad:end -->")) suppressAdvertisement = false;
                continue;
            }
            if (trimmed.contains("<!-- ad:start -->")) {
                suppressAdvertisement = !trimmed.contains("<!-- ad:end -->");
                continue;
            }
            if (suppressHtmlBlock) {
                if (trimmed.toLowerCase().contains("</" + suppressedTag + ">")) {
                    suppressHtmlBlock = false;
                    suppressedTag = null;
                }
                continue;
            }
            String lower = trimmed.toLowerCase();
            if (lower.startsWith("<script") || lower.startsWith("<style")) {
                suppressedTag = lower.startsWith("<script") ? "script" : "style";
                if (!lower.contains("</" + suppressedTag + ">")) suppressHtmlBlock = true;
                continue;
            }
            if (NAVIGATION_LINK.matcher(line).matches() || ADVERTISEMENT_LINK.matcher(line).matches()) continue;

            String withoutImages = replaceImages(line);
            String withoutBreaks = BREAK_TAG.matcher(withoutImages).replaceAll("\n");
            cleaned.add(SAFE_HTML_TAG.matcher(withoutBreaks).replaceAll(""));
        }
        return trimBlankEdges(String.join("\n", cleaned));
    }

    private String replaceImages(String line) {
        Matcher matcher = IMAGE.matcher(line);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String alt = matcher.group(1).trim();
            String replacement = alt.isEmpty() ? "" : "[图片: " + alt + "]";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String trimBlankEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) start++;
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }
}
