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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PgMetadataFiltersTest {
    @Test
    void shouldBindApprovedKeysAndValuesInsteadOfConcatenatingThem() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "ONLINE");
        filters.put("articleId", 12L);

        PgMetadataFilters.FilterClause clause = PgMetadataFilters.build(filters);

        assertEquals(4, clause.arguments().size());
        assertFalse(clause.sql().contains("ONLINE"));
        assertFalse(clause.sql().contains("12"));
        assertEquals("articleId", clause.arguments().get(0));
        assertEquals("12", clause.arguments().get(1));
    }

    @Test
    void shouldRejectUnknownOrNullMetadata() {
        assertThrows(ClientException.class, () -> PgMetadataFilters.build(Map.of("eventId", "secret")));
        Map<String, Object> nullValue = new LinkedHashMap<>();
        nullValue.put("status", null);
        assertThrows(ClientException.class, () -> PgMetadataFilters.build(nullValue));
    }
}
