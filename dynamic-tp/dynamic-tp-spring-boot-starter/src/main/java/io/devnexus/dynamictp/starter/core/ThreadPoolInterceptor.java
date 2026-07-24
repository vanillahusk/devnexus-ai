package io.devnexus.dynamictp.starter.core;

import java.util.concurrent.ThreadPoolExecutor;

import io.devnexus.dynamictp.starter.metrics.DynamicThreadPoolMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ThreadPoolInterceptor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolInterceptor.class);

    private final ThreadPoolRegistry registry;
    private final DynamicThreadPoolMetrics metrics;

    public ThreadPoolInterceptor(ThreadPoolRegistry registry, DynamicThreadPoolMetrics metrics) {
        this.registry = registry;
        this.metrics = metrics;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ThreadPoolExecutor) {
            register(beanName, (ThreadPoolExecutor) bean);
        } else if (bean instanceof ThreadPoolTaskExecutor) {
            ThreadPoolExecutor executor = ((ThreadPoolTaskExecutor) bean).getThreadPoolExecutor();
            if (executor != null) {
                register(beanName, executor);
            }
        }
        return bean;
    }

    private void register(String beanName, ThreadPoolExecutor executor) {
        registry.register(beanName, executor);
        metrics.bind(registry.getManaged(beanName));
        log.info("Successfully registered business thread pool [{}]", beanName);
        if (!(executor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue)) {
            log.warn("Thread pool [{}] is not using ResizableCapacityLinkedBlockingQueue, queue capacity cannot be updated dynamically", beanName);
        }
    }
}