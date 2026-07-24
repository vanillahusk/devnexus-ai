package com.github.paicoding.forum.service.ai.index;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Generation 重建后台任务入口。
 *
 * <p>同一 JVM 同时只允许一个任务，管理接口立即返回，避免全量构建占用 HTTP 线程。
 * 任务状态仅用于本次进程内运维观察；Ragent 中的 Generation 状态才是持久化事实。</p>
 */
@Service
@ConditionalOnProperty(name = "ai.knowledge.generation-rebuild.enabled", havingValue = "true")
public class ArticleKnowledgeGenerationRebuildTaskService {
    private static final Pattern GENERATION_LABEL = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    private final ArticleKnowledgeGenerationRebuildService rebuildService;
    private final Executor executor;
    private final AtomicReference<TaskSnapshot> snapshot = new AtomicReference<>(TaskSnapshot.idle());

    public ArticleKnowledgeGenerationRebuildTaskService(ArticleKnowledgeGenerationRebuildService rebuildService) {
        this(rebuildService, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "article-knowledge-generation-rebuild");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ArticleKnowledgeGenerationRebuildTaskService(ArticleKnowledgeGenerationRebuildService rebuildService,
                                                  Executor executor) {
        this.rebuildService = rebuildService;
        this.executor = executor;
    }

    public TaskSnapshot submit(String generationLabel) {
        if (generationLabel == null || !GENERATION_LABEL.matcher(generationLabel).matches()) {
            throw new IllegalArgumentException("generationLabel必须是1-40位小写字母、数字或连字符");
        }
        String taskId = UUID.randomUUID().toString();
        TaskSnapshot queued = new TaskSnapshot(taskId, generationLabel, TaskStatus.QUEUED,
                Instant.now(), null, null, null, null);
        while (true) {
            TaskSnapshot current = snapshot.get();
            if (current.status().active()) {
                throw new IllegalStateException("已有Generation重建任务正在执行: " + current.taskId());
            }
            if (snapshot.compareAndSet(current, queued)) break;
        }
        try {
            executor.execute(() -> run(queued));
            return queued;
        } catch (RejectedExecutionException failure) {
            TaskSnapshot rejected = queued.failed("任务执行器已关闭或队列拒绝");
            snapshot.compareAndSet(queued, rejected);
            throw new IllegalStateException(rejected.errorSummary(), failure);
        }
    }

    public TaskSnapshot status() {
        return snapshot.get();
    }

    private void run(TaskSnapshot queued) {
        TaskSnapshot running = queued.running();
        if (!snapshot.compareAndSet(queued, running)) return;
        try {
            ArticleKnowledgeGenerationRebuildService.RebuildResult result =
                    rebuildService.rebuild(queued.generationLabel());
            snapshot.compareAndSet(running, running.succeeded(result));
        } catch (RuntimeException failure) {
            snapshot.compareAndSet(running, running.failed(failure.getClass().getSimpleName()
                    + ": Generation重建失败，请查看服务端脱敏日志"));
        }
    }

    @PreDestroy
    public void shutdown() {
        if (executor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }

    public enum TaskStatus {
        IDLE, QUEUED, RUNNING, SUCCEEDED, FAILED;

        boolean active() {
            return this == QUEUED || this == RUNNING;
        }
    }

    public record TaskSnapshot(String taskId, String generationLabel, TaskStatus status,
                               Instant submittedAt, Instant startedAt, Instant finishedAt,
                               ArticleKnowledgeGenerationRebuildService.RebuildResult result,
                               String errorSummary) {
        static TaskSnapshot idle() {
            return new TaskSnapshot(null, null, TaskStatus.IDLE, null, null, null, null, null);
        }

        TaskSnapshot running() {
            return new TaskSnapshot(taskId, generationLabel, TaskStatus.RUNNING,
                    submittedAt, Instant.now(), null, null, null);
        }

        TaskSnapshot succeeded(ArticleKnowledgeGenerationRebuildService.RebuildResult result) {
            return new TaskSnapshot(taskId, generationLabel, TaskStatus.SUCCEEDED,
                    submittedAt, startedAt, Instant.now(), result, null);
        }

        TaskSnapshot failed(String errorSummary) {
            return new TaskSnapshot(taskId, generationLabel, TaskStatus.FAILED,
                    submittedAt, startedAt, Instant.now(), null, errorSummary);
        }
    }
}
