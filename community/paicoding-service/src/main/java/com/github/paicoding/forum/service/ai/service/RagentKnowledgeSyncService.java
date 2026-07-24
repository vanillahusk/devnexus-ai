package com.github.paicoding.forum.service.ai.service;

import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;

import java.util.List;
import java.util.Map;

/**
 * 社区知识文档同步到 ragent
 *
 * @author Codex
 * @date 2026-04-01
 */
public interface RagentKnowledgeSyncService {

    void autoSync(AiKnowledgeDocDTO doc);

    void sync(AiKnowledgeDocDTO doc);

    /** 快照重建专用：只写入指定的物理 Generation，不覆盖在线文档映射。 */
    void syncToGeneration(AiKnowledgeDocDTO doc, String physicalCollection);

    void deleteFromGeneration(String configKey, String physicalCollection);

    void deleteByConfigKey(String configKey);

    /** 删除失败必须抛出异常，供可靠消息消费者触发 RocketMQ 重试。 */
    void deleteStrictByConfigKey(String configKey);

    void syncAll(List<AiKnowledgeDocDTO> docs);

    GenerationState beginGeneration(String generationLabel, long startWatermark);

    GenerationState recordGenerationProgress(String generationLabel, long appliedWatermark,
                                             long targetWatermark, boolean reconciled);

    GenerationState activateGeneration(String generationLabel);

    GenerationState failGeneration(String generationLabel);

    Map<Long, ArticleVersionSummary> generationArticleVersions(String physicalCollection);

    record GenerationState(String logicalCollection, String activeGeneration, String buildingGeneration,
                           String previousGeneration, String status, long startWatermark,
                           long appliedWatermark, long targetWatermark, boolean reconciled) {
    }

    record ArticleVersionSummary(long minVersion, long maxVersion, long chunkCount) {
    }
}
