package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.service.ai.index.ArticleMarkdownSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleMarkdownSanitizerTest {
    private final ArticleMarkdownSanitizer sanitizer = new ArticleMarkdownSanitizer();

    @Test
    void shouldRemoveExecutableHtmlNavigationAndImageUrlButKeepAlt() {
        String markdown = "<script>alert('x')</script>\n# 标题\n![架构图](https://secret.example/a.png)\n"
                + "[返回顶部](#top)\n广告：[购买](https://ad.example)\n"
                + "<!-- ad:start -->\n整段推广\n<!-- ad:end -->\n<div>正文</div>";

        String cleaned = sanitizer.sanitize(markdown);

        assertFalse(cleaned.contains("script"));
        assertFalse(cleaned.contains("secret.example"));
        assertFalse(cleaned.contains("返回顶部"));
        assertFalse(cleaned.contains("ad.example"));
        assertFalse(cleaned.contains("整段推广"));
        assertTrue(cleaned.contains("# 标题"));
        assertTrue(cleaned.contains("[图片: 架构图]"));
        assertTrue(cleaned.contains("正文"));
    }

    @Test
    void shouldPreserveCodeFenceListsAndTables() {
        String markdown = "- 列表\n\n|列|值|\n|-|-|\n|A|B|\n\n```html\n<script>keep()</script>\n```";

        String cleaned = sanitizer.sanitize(markdown);

        assertTrue(cleaned.contains("- 列表"));
        assertTrue(cleaned.contains("|A|B|"));
        assertTrue(cleaned.contains("<script>keep()</script>"));
    }

    @Test
    void shouldBeIdempotent() {
        String once = sanitizer.sanitize("<p>正文</p>\n![图](https://example/a.png)");
        assertEquals(once, sanitizer.sanitize(once));
    }
}
