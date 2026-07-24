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

package com.nageoffer.ai.ragent.rag.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * HTTP客户端配置类
 * 用于配置OkHttpClient的全局实例，设置连接超时、读取超时和写入超时等参数
 */
@Configuration
public class HttpClientConfig {

    /**
     * 创建并配置OkHttpClient实例
     *
     * @return 配置好的OkHttpClient实例
     */
    @Bean
    public OkHttpClient okHttpClient(
            @Value("${ai.http.connect-timeout:5s}") Duration connectTimeout,
            @Value("${ai.http.write-timeout:30s}") Duration writeTimeout,
            @Value("${ai.http.read-timeout:60s}") Duration readTimeout,
            @Value("${ai.http.call-timeout:75s}") Duration callTimeout) {
        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeout)
                .writeTimeout(writeTimeout)
                .readTimeout(readTimeout)
                .callTimeout(callTimeout)
                .retryOnConnectionFailure(true)
                .build();
    }
}
