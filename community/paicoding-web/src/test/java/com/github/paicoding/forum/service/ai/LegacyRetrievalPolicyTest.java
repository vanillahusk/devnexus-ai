package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.service.ai.retrieval.LegacyRetrievalPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyRetrievalPolicyTest {

    @Test
    void shouldNormalizeLineBreaksTrimAndLimitSearchKeyTo32Characters() {
        String question = "  RocketMQ消息积压后如何恢复？\n还要验证重复消费与最终一致性，这是额外字符  ";

        String actual = LegacyRetrievalPolicy.normalizeSearchKey(question);

        assertEquals(32, actual.length());
        assertEquals("RocketMQ消息积压后如何恢复？ 还要验证重复消费与最终一致", actual);
    }

    @Test
    void shouldFreezeLegacyKeywordScoreWeights() {
        assertEquals(15, LegacyRetrievalPolicy.score(
                "RocketMQ 积压", "RocketMQ故障", "消息积压恢复与监控"));
        assertEquals(65, LegacyRetrievalPolicy.score(
                "RocketMQ", "RocketMQ可靠消息", "使用RocketMQ完成削峰"));
        assertEquals(0, LegacyRetrievalPolicy.score(" ", "任意标题", "任意正文"));
    }

    @Test
    void shouldNormalizeAndLimitSnippet() {
        assertEquals("第一段 第二段...", LegacyRetrievalPolicy.limitSnippet(" 第一段\n\n第二段内容 ", 7));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyRetrievalPolicy.limitSnippet("content", -1));
    }
}
