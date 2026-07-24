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

package com.nageoffer.ai.ragent.rag.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPaicodingArticleClientTest {
    @Test
    void shouldSendInternalTokenAndParseBoundedOnlineSnapshot() {
        AtomicReference<okhttp3.Request> captured = new AtomicReference<>();
        String json = "{\"status\":{},\"result\":{\"articleId\":1001,\"articleVersion\":8,"
                + "\"title\":\"Redis\",\"summary\":\"恢复\",\"content\":\"正文\","
                + "\"categoryId\":3,\"updatedAt\":123}}";
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            captured.set(chain.request());
            return response(chain.request(), 200, json);
        }).build();
        HttpPaicodingArticleClient client = client(http, "secret");

        PaicodingArticleClient.ArticleSnapshot result = client.findOnlineArticle(1001).orElseThrow();

        assertEquals(8, result.articleVersion());
        assertEquals("secret", captured.get().header("X-AIGC-INTERNAL-TOKEN"));
        assertEquals("/internal/aigc/knowledge/articles/1001", captured.get().url().encodedPath());
    }

    @Test
    void shouldMapNotFoundToInvisibleAndRejectMissingCredential() {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
                response(chain.request(), 404, "")).build();
        assertTrue(client(http, "secret").findOnlineArticle(1001).isEmpty());
        assertThrows(IllegalStateException.class, () -> client(http, "").findOnlineArticle(1001));
    }

    private HttpPaicodingArticleClient client(OkHttpClient http, String token) {
        HttpPaicodingArticleClient client = new HttpPaicodingArticleClient(http, new ObjectMapper());
        ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:8080");
        ReflectionTestUtils.setField(client, "tokenHeader", "X-AIGC-INTERNAL-TOKEN");
        ReflectionTestUtils.setField(client, "token", token);
        return client;
    }

    private Response response(okhttp3.Request request, int code, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }
}
