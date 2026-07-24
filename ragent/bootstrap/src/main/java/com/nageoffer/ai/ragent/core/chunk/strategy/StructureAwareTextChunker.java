/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.core.chunk.strategy;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.core.chunk.ChunkingMode;
import com.nageoffer.ai.ragent.core.chunk.ChunkingOptions;
import com.nageoffer.ai.ragent.core.chunk.ChunkingStrategy;
import com.nageoffer.ai.ragent.core.chunk.TextBoundaryOptions;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * 结构感知分块器（Markdown 友好版）
 * - 绝不改写文本，只在"块"边界切分
 * - 块类型：Heading、Paragraph（空行分段）、CodeFence（```...```）、Atomic（整行 ![]()/[]()）
 * - 通过 min/target/max 预算控制 chunk 大小
 * - 支持可选的 overlap
 */
@Component
@RequiredArgsConstructor
public class StructureAwareTextChunker implements ChunkingStrategy {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.*$");
    private static final Pattern CODE_FENCE = Pattern.compile("^```.*$");
    private static final Pattern ATOMIC_IMAGE = Pattern.compile("^!\\[[^]]*]\\([^)]+\\)(?:\\s*\"[^\"]*\")?\\s*$");
    private static final Pattern ATOMIC_LINK = Pattern.compile("^\\[[^]]+]\\([^)]+\\)\\s*$");
    private final TokenCounterService tokenCounterService;

    @Override
    public ChunkingMode getType() {
        return ChunkingMode.STRUCTURE_AWARE;
    }

    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (StrUtil.isBlank(text)) return List.of();

        // 统一行尾：Windows \r\n → \n，老 Mac \r → \n，避免 \r 残留导致空行/标题识别失败
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        FrontMatter frontMatter = extractFrontMatter(text);
        text = frontMatter.body();

        TextBoundaryOptions opts = (TextBoundaryOptions) config;
        int effectiveTarget = opts.targetChars();
        int effectiveMax = opts.maxChars();
        int effectiveMin = opts.minChars();
        int effectiveOverlap = opts.overlapChars();
        boolean tokenAware = opts.targetTokens() > 0 && opts.maxTokens() > 0;

        // 1) 扫描成“块”（记录原文的 start/end 下标，确保输出 substring 完全等于原文）
        List<Block> blocks = segmentToBlocks(text);
        blocks = tokenAware
                ? splitOversizedParagraphsByTokens(blocks, text, opts.minTokens(),
                opts.targetTokens(), opts.maxTokens())
                : splitOversizedParagraphs(blocks, text, effectiveMin, effectiveTarget, effectiveMax);

        if (blocks.isEmpty()) {
            VectorChunk chunk = VectorChunk.builder()
                    .content(text)
                    .index(0)
                    .chunkId(IdUtil.getSnowflakeNextIdStr())
                    .build();
            return List.of(chunk); // 极端兜底：整体作为一个块
        }

        // 2) 依据 min/target/max 打包成 chunk（只在块边界切分）
        List<int[]> ranges = tokenAware
                ? packBlocksToChunksByTokens(blocks, text, opts.minTokens(),
                opts.targetTokens(), opts.maxTokens())
                : packBlocksToChunks(blocks, text.length(), effectiveMin, effectiveTarget, effectiveMax);

        // 3)（可选）加入重叠：为保持“只在块边界切分”，这里不在中间加重叠，若开启 overlap，仅复制“上一 chunk 的尾部全文子串”到下一 chunk 的开头
        List<VectorChunk> out = tokenAware
                ? materializeByTokens(text, ranges, opts.overlapTokens(), opts.maxTokens(), frontMatter.metadata())
                : materialize(text, ranges, effectiveOverlap, frontMatter.metadata());

        // 编号从 0 递增
        for (int i = 0; i < out.size(); i++) {
            VectorChunk chunk = VectorChunk.builder()
                    .content(out.get(i).getContent())
                    .index(i)
                    .chunkId(IdUtil.getSnowflakeNextIdStr())
                    .metadata(out.get(i).getMetadata())
                    .build();
            out.set(i, chunk);
        }
        return out;
    }

    // ----------- 块模型 -----------
    @Getter
    @ToString
    @AllArgsConstructor
    private static class Block {
        enum Kind {HEADING, CODE, ATOMIC, PARA}

        final Block.Kind kind;
        final int start;   // 在原文中的起始（含）
        final int end;     // 在原文中的结束（不含）
    }

    private record FrontMatter(String body, Map<String, Object> metadata) {
    }

    private FrontMatter extractFrontMatter(String text) {
        if (!text.startsWith("---\n")) return new FrontMatter(text, Map.of());
        int close = text.indexOf("\n---\n", 4);
        if (close < 0) return new FrontMatter(text, Map.of());
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String line : text.substring(4, close).split("\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.matches("[A-Za-z][A-Za-z0-9_]*") || value.isEmpty()) continue;
            metadata.put(key, scalarValue(value));
        }
        return new FrontMatter(text.substring(close + 5), metadata);
    }

    private Object scalarValue(String value) {
        if (value.matches("-?[0-9]+")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    // ----------- 1) 线性扫描生成块 -----------
    private List<Block> segmentToBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        int n = text.length();
        int pos = 0;

        boolean inFence = false;
        int fenceStart = -1;

        boolean inPara = false;
        int paraStart = -1;

        while (pos < n) {
            int lineEnd = indexOfNl(text, pos);
            // [pos, lineEnd) 不含换行字符；lineEndNl = 包含换行（若有）
            int lineEndNl = lineEnd < n && text.charAt(lineEnd) == '\n' ? lineEnd + 1 : lineEnd;
            String line = text.substring(pos, lineEnd);

            String trimmed = trimRightKeepLeft(line); // 不改左侧空白，保留原貌；右侧空白不影响判断

            if (!inFence && CODE_FENCE.matcher(trimmed).matches()) {
                // 先把正在积累的段落收尾
                if (inPara) {
                    blocks.add(new Block(Block.Kind.PARA, paraStart, pos));
                    inPara = false;
                }
                // 进入代码围栏
                inFence = true;
                fenceStart = pos;
                pos = lineEndNl;
                continue;
            }

            if (inFence) {
                // 直到遇到 fence 结束行
                if (CODE_FENCE.matcher(trimmed).matches()) {
                    // 包含结束 fence 行
                    blocks.add(new Block(Block.Kind.CODE, fenceStart, lineEndNl));
                    inFence = false;
                }
                pos = lineEndNl;
                continue;
            }

            // 空行 => 段落边界
            if (trimmed.isEmpty()) {
                if (inPara) {
                    blocks.add(new Block(Block.Kind.PARA, paraStart, pos));
                    inPara = false;
                }
                // 空行本身并入前一块或下一块？——保持原貌：把空行并入前一块（若无前一块，则作为 0 长度过渡）
                pos = lineEndNl;
                continue;
            }

            // 标题/原子行（图片/链接）都作为独立块
            if (HEADING.matcher(trimmed).matches()) {
                if (inPara) {
                    blocks.add(new Block(Block.Kind.PARA, paraStart, pos));
                    inPara = false;
                }
                blocks.add(new Block(Block.Kind.HEADING, pos, lineEndNl));
                pos = lineEndNl;
                continue;
            }
            if (ATOMIC_IMAGE.matcher(trimmed).matches() || ATOMIC_LINK.matcher(trimmed).matches()) {
                if (inPara) {
                    blocks.add(new Block(Block.Kind.PARA, paraStart, pos));
                    inPara = false;
                }
                blocks.add(new Block(Block.Kind.ATOMIC, pos, lineEndNl));
                pos = lineEndNl;
                continue;
            }

            // 其他：并入当前段落
            if (!inPara) {
                inPara = true;
                paraStart = pos;
            }
            pos = lineEndNl;
        }

        // 收尾
        if (inFence) {
            // 未闭合 fence：将剩余部分作为 CODE（保持原样）
            blocks.add(new Block(Block.Kind.CODE, fenceStart, n));
        } else if (inPara) {
            blocks.add(new Block(Block.Kind.PARA, paraStart, n));
        }
        return coalesceTrailingBlanks(blocks, text);
    }

    // 合并“块尾部的若干空行”到块内部，避免单独产生空白块（保持原文不变，只是归属到前块）
    private List<Block> coalesceTrailingBlanks(List<Block> blocks, String text) {
        if (blocks.isEmpty()) return blocks;
        List<Block> out = new ArrayList<>();
        Block prev = blocks.get(0);
        for (int i = 1; i < blocks.size(); i++) {
            Block cur = blocks.get(i);
            if (isAllBlank(text, prev.end, cur.start)) {
                // 把中间空白并入 prev，但别丢掉 cur
                prev = new Block(prev.kind, prev.start, cur.start);
            }
            // 无论是否并入空白，prev 都该进结果，然后向前推进
            out.add(prev);
            prev = cur;
        }
        out.add(prev);
        return out;
    }

    // ----------- 2) 打包成 chunk（仅在块边界切） -----------
    private List<int[]> packBlocksToChunks(List<Block> blocks, int textLen, int min, int target, int max) {
        List<int[]> ranges = new ArrayList<>();
        int i = 0;
        while (i < blocks.size()) {
            int chunkStart = blocks.get(i).start;
            int chunkEnd = blocks.get(i).end; // 不含
            int size = chunkEnd - chunkStart;

            int j = i + 1;
            while (j < blocks.size()) {
                Block b = blocks.get(j);
                int afterAdd = (b.end - chunkStart); // 等同于 size + nextSize + 中间空白（已包含）

                if (afterAdd <= max) {
                    // 还能加
                    chunkEnd = b.end;
                    size = afterAdd;
                    j++;
                } else {
                    // 超过 max：若当前 size < min，则“忍一次超限”，把这个块也吸进去（保证不要太小）
                    if (size < min) {
                        chunkEnd = b.end;
                        size = afterAdd;
                        j++;
                    }
                    break;
                }
            }

            ranges.add(new int[]{chunkStart, chunkEnd});
            i = j;
        }

        // 若最后一个 chunk 明显过小，尝试与前一个合并（仍不跨越 max 过多）
        if (ranges.size() >= 2) {
            int[] last = ranges.get(ranges.size() - 1);
            if (last[1] - last[0] < Math.min(min, target / 2)) {
                int[] prev = ranges.get(ranges.size() - 2);
                if (last[1] - prev[0] <= max * 2) { // 放宽一下，尽量合并到可接受大小
                    prev[1] = last[1];
                    ranges.remove(ranges.size() - 1);
                }
            }
        }
        return ranges;
    }

    /**
     * 普通长段落可以按句末/换行继续拆分；代码块、表格等结构块不做硬切，避免破坏语义结构。
     */
    private List<Block> splitOversizedParagraphs(List<Block> blocks, String text,
                                                 int min, int target, int max) {
        List<Block> result = new ArrayList<>();
        for (Block block : blocks) {
            if (block.kind != Block.Kind.PARA || block.end - block.start <= max) {
                result.add(block);
                continue;
            }
            int start = block.start;
            while (block.end - start > max) {
                int cut = findParagraphCut(text, start, block.end, min, target, max);
                result.add(new Block(Block.Kind.PARA, start, cut));
                start = cut;
            }
            if (start < block.end) {
                result.add(new Block(Block.Kind.PARA, start, block.end));
            }
        }
        return result;
    }

    private int findParagraphCut(String text, int start, int end, int min, int target, int max) {
        int lower = Math.min(end, start + Math.max(1, min));
        int preferred = Math.min(end, start + Math.max(min, target));
        int upper = Math.min(end, start + max);
        for (int i = preferred; i < upper; i++) {
            if (isSoftBoundary(text.charAt(i))) return i + 1;
        }
        for (int i = preferred - 1; i >= lower; i--) {
            if (isSoftBoundary(text.charAt(i))) return i + 1;
        }
        return upper;
    }

    private List<Block> splitOversizedParagraphsByTokens(List<Block> blocks, String text,
                                                          int min, int target, int max) {
        List<Block> result = new ArrayList<>();
        for (Block block : blocks) {
            if (block.kind != Block.Kind.PARA || tokenCount(text, block.start, block.end) <= max) {
                result.add(block);
                continue;
            }
            int start = block.start;
            while (tokenCount(text, start, block.end) > max) {
                int cut = findTokenParagraphCut(text, start, block.end, min, target, max);
                if (cut <= start) cut = maxEndByTokens(text, start, block.end, max);
                result.add(new Block(Block.Kind.PARA, start, cut));
                start = cut;
            }
            if (start < block.end) result.add(new Block(Block.Kind.PARA, start, block.end));
        }
        return result;
    }

    private int findTokenParagraphCut(String text, int start, int end, int min, int target, int max) {
        int lower = maxEndByTokens(text, start, end, Math.max(1, min));
        int preferred = maxEndByTokens(text, start, end, Math.max(min, target));
        int upper = maxEndByTokens(text, start, end, max);
        for (int i = preferred; i < upper; i++) {
            if (isSoftBoundary(text.charAt(i))) return i + 1;
        }
        for (int i = Math.min(preferred - 1, upper - 1); i >= lower; i--) {
            if (isSoftBoundary(text.charAt(i))) return i + 1;
        }
        return upper;
    }

    private int maxEndByTokens(String text, int start, int end, int budget) {
        int low = Math.min(start + 1, end);
        int high = end;
        int best = low;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (tokenCount(text, start, mid) <= budget) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private List<int[]> packBlocksToChunksByTokens(List<Block> blocks, String text,
                                                    int min, int target, int max) {
        List<int[]> ranges = new ArrayList<>();
        int i = 0;
        while (i < blocks.size()) {
            int start = blocks.get(i).start;
            int end = blocks.get(i).end;
            int tokens = tokenCount(text, start, end);
            int j = i + 1;
            while (j < blocks.size()) {
                Block next = blocks.get(j);
                int after = tokenCount(text, start, next.end);
                if (after <= target || (tokens < min && after <= max)) {
                    end = next.end;
                    tokens = after;
                    j++;
                } else {
                    break;
                }
            }
            ranges.add(new int[]{start, end});
            i = j;
        }
        if (ranges.size() >= 2) {
            int[] last = ranges.get(ranges.size() - 1);
            int[] previous = ranges.get(ranges.size() - 2);
            if (tokenCount(text, last[0], last[1]) < min
                    && tokenCount(text, previous[0], last[1]) <= max) {
                previous[1] = last[1];
                ranges.remove(ranges.size() - 1);
            }
        }
        return ranges;
    }

    private boolean isSoftBoundary(char c) {
        return c == '\n' || c == '。' || c == '！' || c == '？' || c == '；'
                || c == '.' || c == '!' || c == '?' || c == ';' || Character.isWhitespace(c);
    }

    // ----------- 3) 物化为 Chunk，必要时追加 overlap（复制原文尾部） -----------
    private List<VectorChunk> materialize(String text, List<int[]> ranges, int overlap,
                                          Map<String, Object> documentMetadata) {
        if (ranges.isEmpty()) return List.of();
        List<VectorChunk> out = new ArrayList<>();
        String prevTail = null;

        for (int k = 0; k < ranges.size(); k++) {
            int s = ranges.get(k)[0];
            int e = ranges.get(k)[1];
            String body = text.substring(s, e);
            if (overlap > 0 && prevTail != null && !prevTail.isEmpty()) {
                body = prevTail + body;
            }

            Map<String, Object> metadata = new LinkedHashMap<>(documentMetadata);
            metadata.put("headingPath", headingPathAt(text, s));
            metadata.put("contentHash", sha256(body));
            metadata.put("charCount", body.length());
            VectorChunk chunk = VectorChunk.builder()
                    .content(body)
                    .index(k)
                    .chunkId(IdUtil.getSnowflakeNextIdStr())
                    .metadata(metadata)
                    .build();
            out.add(chunk);

            // 计算下一块的 overlap 尾部（完全来自本 chunk 原文结尾）
            if (overlap > 0) {
                prevTail = tailByChars(text.substring(s, e), overlap);
            }
        }
        return out;
    }

    private List<VectorChunk> materializeByTokens(String text, List<int[]> ranges, int overlapTokens,
                                                   int maxTokens, Map<String, Object> documentMetadata) {
        if (ranges.isEmpty()) return List.of();
        List<VectorChunk> out = new ArrayList<>();
        String previousBody = null;
        for (int k = 0; k < ranges.size(); k++) {
            int start = ranges.get(k)[0];
            int end = ranges.get(k)[1];
            String body = text.substring(start, end);
            int bodyTokens = countTokens(body);
            int appliedOverlapTokens = 0;
            if (previousBody != null && overlapTokens > 0 && bodyTokens < maxTokens) {
                String overlap = tailByTokens(previousBody, Math.min(overlapTokens, maxTokens - bodyTokens));
                appliedOverlapTokens = countTokens(overlap);
                body = overlap + body;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(documentMetadata);
            metadata.put("headingPath", headingPathAt(text, start));
            metadata.put("contentHash", sha256(body));
            metadata.put("charCount", body.length());
            metadata.put("tokenCount", countTokens(body));
            metadata.put("overlapTokenCount", appliedOverlapTokens);
            out.add(VectorChunk.builder()
                    .content(body)
                    .index(k)
                    .chunkId(IdUtil.getSnowflakeNextIdStr())
                    .metadata(metadata)
                    .build());
            previousBody = text.substring(start, end);
        }
        return out;
    }

    private String headingPathAt(String text, int position) {
        String[] path = new String[6];
        int cursor = 0;
        while (cursor <= position && cursor < text.length()) {
            int lineEnd = indexOfNl(text, cursor);
            String line = trimRightKeepLeft(text.substring(cursor, lineEnd));
            if (HEADING.matcher(line).matches()) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                path[level - 1] = line.substring(level).trim();
                for (int i = level; i < path.length; i++) path[i] = null;
            }
            if (lineEnd >= text.length()) break;
            cursor = lineEnd + 1;
        }
        List<String> headings = new ArrayList<>();
        for (String heading : path) {
            if (heading != null && !heading.isBlank()) headings.add(heading);
        }
        return String.join(" > ", headings);
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    // ----------- 小工具 -----------
    private int indexOfNl(String s, int from) {
        int p = s.indexOf('\n', from);
        return p < 0 ? s.length() : p;
    }

    private String trimRightKeepLeft(String s) {
        int r = s.length();
        while (r > 0 && Character.isWhitespace(s.charAt(r - 1)) && s.charAt(r - 1) != '\n' && s.charAt(r - 1) != '\r') {
            r--;
        }
        return s.substring(0, r);
    }

    private boolean isAllBlank(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (!(c == ' ' || c == '\t' || c == '\r' || c == '\n')) return false;
        }
        return true;
    }

    private String tailByChars(String s, int n) {
        if (n <= 0) return "";
        int len = s.length();
        return len <= n ? s : s.substring(len - n);
    }

    private String tailByTokens(String text, int budget) {
        if (budget <= 0 || text.isEmpty()) return "";
        int low = 0;
        int high = text.length();
        int best = text.length();
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (countTokens(text.substring(mid)) <= budget) {
                best = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return text.substring(best);
    }

    private int tokenCount(String text, int start, int end) {
        return countTokens(text.substring(start, end));
    }

    private int countTokens(String text) {
        Integer count = tokenCounterService.countTokens(text);
        if (count == null) throw new IllegalStateException("Token counter returned null");
        return count;
    }
}
