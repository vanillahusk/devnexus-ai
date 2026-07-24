package com.github.paicoding.forum.service.ai.service.impl;

import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.SearchAiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeAdminService;
import com.github.paicoding.forum.service.ai.service.RagentKnowledgeSyncService;
import com.github.paicoding.forum.service.config.repository.dao.ConfigDao;
import com.github.paicoding.forum.service.config.repository.entity.GlobalConfigDO;
import com.github.paicoding.forum.service.config.repository.params.SearchGlobalConfigParams;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 知识库管理实现
 *
 * @author Codex
 * @date 2026-04-01
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeAdminServiceImpl implements AiKnowledgeAdminService {
    private static final Set<String> SUPPORTED_TYPES = Set.of("rule", "faq", "qa");

    private final ConfigDao configDao;
    private final AiKnowledgeProperties properties;
    private final RagentKnowledgeSyncService ragentKnowledgeSyncService;

    @Override
    public PageVo<AiKnowledgeDocDTO> list(SearchAiKnowledgeDocReq req) {
        long pageNum = req == null || req.getPageNumber() == null ? 1L : req.getPageNumber();
        long pageSize = req == null || req.getPageSize() == null ? 10L : req.getPageSize();

        List<AiKnowledgeDocDTO> all = loadAllKnowledgeDocs();
        if (req != null && StringUtils.isNotBlank(req.getType())) {
            String type = normalizeType(req.getType());
            all = all.stream().filter(doc -> Objects.equals(doc.getType(), type)).collect(Collectors.toList());
        }
        if (req != null && StringUtils.isNotBlank(req.getKeyword())) {
            String keyword = req.getKeyword().trim().toLowerCase(Locale.ROOT);
            all = all.stream()
                    .filter(doc -> containsIgnoreCase(doc.getTitle(), keyword)
                            || containsIgnoreCase(doc.getContent(), keyword)
                            || containsIgnoreCase(doc.getCode(), keyword))
                    .collect(Collectors.toList());
        }

        int from = (int) Math.max(0, (pageNum - 1) * pageSize);
        int to = (int) Math.min(all.size(), from + pageSize);
        List<AiKnowledgeDocDTO> pageList = from >= all.size() ? Collections.emptyList() : all.subList(from, to);
        return PageVo.build(pageList, pageSize, pageNum, all.size());
    }

    @Override
    public void save(AiKnowledgeDocReq req) {
        if (req == null || StringUtils.isAnyBlank(req.getType(), req.getTitle(), req.getContent())) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "知识文档缺少必要字段");
        }
        String type = normalizeType(req.getType());
        String code = sanitizeCode(StringUtils.defaultIfBlank(req.getCode(), req.getTitle()));
        String key = buildConfigKey(type, code);

        GlobalConfigDO target = req.getId() == null ? configDao.getGlobalConfigByKey(key) : configDao.getGlobalConfigById(req.getId());
        if (target == null) {
            target = new GlobalConfigDO();
            target.setKey(key);
        }
        target.setComment(req.getTitle().trim());
        target.setValue(req.getContent().trim());

        if (target.getId() == null) {
            configDao.save(target);
        } else {
            configDao.updateById(target);
        }
        ragentKnowledgeSyncService.autoSync(toDocDTO(target));
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "知识文档id不能为空");
        }
        GlobalConfigDO globalConfigDO = configDao.getGlobalConfigById(id);
        if (globalConfigDO == null || !StringUtils.startsWith(globalConfigDO.getKey(), properties.getConfigPrefix())) {
            throw ExceptionUtil.of(StatusEnum.RECORDS_NOT_EXISTS, "知识文档不存在");
        }
        configDao.delete(globalConfigDO);
        ragentKnowledgeSyncService.deleteByConfigKey(globalConfigDO.getKey());
    }

    @Override
    public List<AiKnowledgeDocDTO> exportDocs() {
        return loadAllKnowledgeDocs().stream().peek(this::fillExportMarkdown).collect(Collectors.toList());
    }

    @Override
    public void sync(Long id) {
        GlobalConfigDO globalConfigDO = configDao.getGlobalConfigById(id);
        if (globalConfigDO == null || !StringUtils.startsWith(globalConfigDO.getKey(), properties.getConfigPrefix())) {
            throw ExceptionUtil.of(StatusEnum.RECORDS_NOT_EXISTS, "知识文档不存在");
        }
        ragentKnowledgeSyncService.sync(toDocDTO(globalConfigDO));
    }

    @Override
    public void syncAll() {
        ragentKnowledgeSyncService.syncAll(exportDocs());
    }

    private List<AiKnowledgeDocDTO> loadAllKnowledgeDocs() {
        SearchGlobalConfigParams params = new SearchGlobalConfigParams();
        params.setKey(properties.getConfigPrefix());
        params.setPageNum(1L);
        params.setPageSize(200L);
        params.setOffset(0L);
        params.setLimit(200L);

        List<GlobalConfigDO> configs = configDao.listGlobalConfig(params);
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }

        List<AiKnowledgeDocDTO> result = new ArrayList<>(configs.size());
        for (GlobalConfigDO config : configs) {
            if (!StringUtils.startsWith(config.getKey(), properties.getConfigPrefix())) {
                continue;
            }
            AiKnowledgeDocDTO dto = new AiKnowledgeDocDTO();
            dto.setId(config.getId());
            dto.setKey(config.getKey());
            dto.setTitle(config.getComment());
            dto.setContent(config.getValue());

            String suffix = StringUtils.removeStart(config.getKey(), properties.getConfigPrefix());
            String[] pieces = suffix.split("\\.", 2);
            dto.setType(pieces != null && pieces.length > 0 ? pieces[0] : "rule");
            dto.setCode(pieces != null && pieces.length > 1 ? pieces[1] : suffix);
            fillExportMarkdown(dto);
            result.add(dto);
        }
        return result;
    }

    private AiKnowledgeDocDTO toDocDTO(GlobalConfigDO config) {
        AiKnowledgeDocDTO dto = new AiKnowledgeDocDTO();
        dto.setId(config.getId());
        dto.setKey(config.getKey());
        dto.setTitle(config.getComment());
        dto.setContent(config.getValue());

        String suffix = StringUtils.removeStart(config.getKey(), properties.getConfigPrefix());
        String[] pieces = suffix.split("\\.", 2);
        dto.setType(pieces.length > 0 ? pieces[0] : "rule");
        dto.setCode(pieces.length > 1 ? pieces[1] : suffix);
        fillExportMarkdown(dto);
        return dto;
    }

    private void fillExportMarkdown(AiKnowledgeDocDTO dto) {
        dto.setExportMarkdown("## " + dto.getTitle() + "\n"
                + "- 类型: " + dto.getType() + "\n"
                + "- 编码: " + dto.getCode() + "\n"
                + "- 来源Key: " + dto.getKey() + "\n\n"
                + dto.getContent());
    }

    private String normalizeType(String type) {
        String normalized = StringUtils.trimToEmpty(type).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "知识文档类型仅支持 rule / faq / qa");
        }
        return normalized;
    }

    private String buildConfigKey(String type, String code) {
        return properties.getConfigPrefix() + type + "." + code;
    }

    private String sanitizeCode(String raw) {
        String code = StringUtils.trimToEmpty(raw)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5_-]+", "-")
                .replaceAll("-{2,}", "-");
        if (StringUtils.isBlank(code)) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "知识文档编码不能为空");
        }
        return code;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return StringUtils.isNotBlank(source) && source.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
