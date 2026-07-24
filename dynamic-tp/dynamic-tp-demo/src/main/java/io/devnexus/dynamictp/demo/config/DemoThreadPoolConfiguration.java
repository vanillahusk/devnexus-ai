package io.devnexus.dynamictp.demo.config;

import io.devnexus.dynamictp.starter.core.ResizableCapacityLinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoThreadPoolConfiguration {

    @Bean
    public ThreadPoolExecutor orderThreadPool() {
        return new ThreadPoolExecutor(
                2,
                5,
                60,
                TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<Runnable>(10),
                new ThreadPoolExecutor.AbortPolicy());
    }
}