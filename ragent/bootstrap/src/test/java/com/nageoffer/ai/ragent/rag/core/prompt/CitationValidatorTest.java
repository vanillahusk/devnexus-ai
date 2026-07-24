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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationValidatorTest {
    private final CitationValidator validator = new CitationValidator();

    @Test
    void shouldAcceptOnlyReferencesFromCurrentCandidateSet() {
        CitationValidator.Validation valid = validator.validate("使用版本号防乱序。[ref:c1]", List.of(citation("c1")));
        CitationValidator.Validation invalid = validator.validate("伪造来源。[ref:c2]", List.of(citation("c1")));

        assertTrue(valid.valid());
        assertFalse(invalid.valid());
        assertEquals("CITATION_OUT_OF_SCOPE", invalid.code());
        assertEquals(java.util.Set.of("c2"), invalid.unknownChunkIds());
    }

    @Test
    void shouldRejectAnswerWithoutCitation() {
        CitationValidator.Validation result = validator.validate("没有任何引用。", List.of(citation("c1")));

        assertFalse(result.valid());
        assertEquals("CITATION_MISSING", result.code());
    }

    private TrustedRetrievalResult.Citation citation(String id) {
        return new TrustedRetrievalResult.Citation(id, "1001", "1", "title", "heading", "text", 0.1F, null);
    }
}
