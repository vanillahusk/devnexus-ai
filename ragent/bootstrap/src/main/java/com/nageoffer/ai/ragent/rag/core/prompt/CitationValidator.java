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

package com.nageoffer.ai.ragent.rag.core.prompt;

import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 校验模型答案只能引用本次可信检索返回的 Chunk。 */
@Component
public class CitationValidator {
    private static final Pattern CITATION = Pattern.compile("\\[ref:([^\\]\\s]+)]");

    public Validation validate(String answer, List<TrustedRetrievalResult.Citation> allowedCitations) {
        Set<String> allowed = new LinkedHashSet<>();
        if (allowedCitations != null) {
            allowedCitations.stream().map(TrustedRetrievalResult.Citation::chunkId)
                    .filter(id -> id != null && !id.isBlank()).forEach(allowed::add);
        }
        Set<String> referenced = new LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(answer == null ? "" : answer);
        while (matcher.find()) referenced.add(matcher.group(1));
        Set<String> unknown = new LinkedHashSet<>(referenced);
        unknown.removeAll(allowed);
        boolean valid = answer != null && !answer.isBlank() && !referenced.isEmpty() && unknown.isEmpty();
        String code = referenced.isEmpty() ? "CITATION_MISSING" : unknown.isEmpty() ? "VALID" : "CITATION_OUT_OF_SCOPE";
        return new Validation(valid, code, Set.copyOf(referenced), Set.copyOf(unknown));
    }

    public record Validation(boolean valid, String code, Set<String> referencedChunkIds,
                             Set<String> unknownChunkIds) {
    }
}
