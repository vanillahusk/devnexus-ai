package com.github.paicoding.forum.service.ai.retrieval;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * 冻结改造前知识召回的纯函数规则。
 *
 * <p>现代 RAG 的离线评测必须与同一份旧实现基线对比，因此查询截断、关键词
 * 打分和片段截取集中在这里，避免生产链路与评测器各复制一套略有差异的逻辑。</p>
 */
public final class LegacyRetrievalPolicy {
    public static final int MAX_SEARCH_KEY_CHARS = 32;

    private LegacyRetrievalPolicy() {
    }

    public static String normalizeSearchKey(String question) {
        String key = StringUtils.trimToEmpty(question).replaceAll("[\\r\\n]+", " ");
        return key.length() > MAX_SEARCH_KEY_CHARS ? key.substring(0, MAX_SEARCH_KEY_CHARS) : key;
    }

    public static int score(String question, String title, String content) {
        if (StringUtils.isBlank(question)) {
            return 0;
        }
        String normalizedQuestion = question.trim().toLowerCase(Locale.ROOT);
        String normalizedTitle = StringUtils.defaultString(title).toLowerCase(Locale.ROOT);
        String normalizedContent = StringUtils.defaultString(content).toLowerCase(Locale.ROOT);

        int score = 0;
        if (normalizedTitle.contains(normalizedQuestion)) {
            score += 30;
        }
        if (normalizedContent.contains(normalizedQuestion)) {
            score += 20;
        }

        for (String token : normalizedQuestion.split("[\\s,，。！？;；:：]+")) {
            if (token.length() < 2) {
                continue;
            }
            if (normalizedTitle.contains(token)) {
                score += 10;
            }
            if (normalizedContent.contains(token)) {
                score += 5;
            }
        }
        return score;
    }

    public static String limitSnippet(String text, int maxChars) {
        if (maxChars < 0) {
            throw new IllegalArgumentException("maxChars must not be negative");
        }
        String normalized = StringUtils.trimToEmpty(text).replaceAll("\\s+", " ");
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }
}
