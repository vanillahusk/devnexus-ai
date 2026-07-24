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

package com.nageoffer.ai.ragent.rag.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationTraceFilterTest {

    private final CorrelationTraceFilter filter = new CorrelationTraceFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsSafeInboundTraceAndRestoresMdc() throws Exception {
        MDC.put("traceId", "previous-trace");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rag/retrieve/trusted");
        request.addHeader(CorrelationTraceFilter.HEADER, "gateway-trace-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inside = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> inside.set(MDC.get("traceId")));

        assertEquals("gateway-trace-12345678", inside.get());
        assertEquals("gateway-trace-12345678", response.getHeader(CorrelationTraceFilter.HEADER));
        assertEquals("previous-trace", MDC.get("traceId"));
    }

    @Test
    void replacesUnsafeInboundTrace() {
        String generated = CorrelationTraceFilter.resolve("unsafe trace with spaces");
        assertNotEquals("unsafe trace with spaces", generated);
        assertEquals(32, generated.length());
        assertTrue(generated.matches("[a-f0-9]{32}"));
    }
}
