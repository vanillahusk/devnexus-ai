package com.github.paicoding.forum.service.ai.index;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleKnowledgeGenerationRebuildTaskServiceTest {

    @Test
    void shouldReturnImmediatelyAndExposeSuccessfulResult() {
        ArticleKnowledgeGenerationRebuildService rebuild = mock(ArticleKnowledgeGenerationRebuildService.class);
        QueuedExecutor executor = new QueuedExecutor();
        ArticleKnowledgeGenerationRebuildTaskService service =
                new ArticleKnowledgeGenerationRebuildTaskService(rebuild, executor);
        ArticleKnowledgeGenerationRebuildService.RebuildResult result =
                new ArticleKnowledgeGenerationRebuildService.RebuildResult(
                        "g2", "articles--g2", 10, 12, 3, 2, 2, 3);
        when(rebuild.rebuild("g2")).thenReturn(result);

        ArticleKnowledgeGenerationRebuildTaskService.TaskSnapshot accepted = service.submit("g2");
        assertEquals(ArticleKnowledgeGenerationRebuildTaskService.TaskStatus.QUEUED, accepted.status());
        assertNotNull(accepted.taskId());

        executor.runNext();
        assertEquals(ArticleKnowledgeGenerationRebuildTaskService.TaskStatus.SUCCEEDED,
                service.status().status());
        assertEquals(12, service.status().result().finalWatermark());
    }

    @Test
    void shouldRejectConcurrentTask() {
        ArticleKnowledgeGenerationRebuildService rebuild = mock(ArticleKnowledgeGenerationRebuildService.class);
        QueuedExecutor executor = new QueuedExecutor();
        ArticleKnowledgeGenerationRebuildTaskService service =
                new ArticleKnowledgeGenerationRebuildTaskService(rebuild, executor);

        service.submit("g2");

        assertThrows(IllegalStateException.class, () -> service.submit("g3"));
    }

    @Test
    void shouldRejectInvalidLabelBeforeEnqueue() {
        ArticleKnowledgeGenerationRebuildService rebuild = mock(ArticleKnowledgeGenerationRebuildService.class);
        QueuedExecutor executor = new QueuedExecutor();
        ArticleKnowledgeGenerationRebuildTaskService service =
                new ArticleKnowledgeGenerationRebuildTaskService(rebuild, executor);

        assertThrows(IllegalArgumentException.class, () -> service.submit("Generation_2"));
        assertEquals(ArticleKnowledgeGenerationRebuildTaskService.TaskStatus.IDLE, service.status().status());
        assertEquals(0, executor.tasks.size());
    }

    @Test
    void shouldExposeSanitizedFailureWithoutRethrowingOnWorker() {
        ArticleKnowledgeGenerationRebuildService rebuild = mock(ArticleKnowledgeGenerationRebuildService.class);
        QueuedExecutor executor = new QueuedExecutor();
        ArticleKnowledgeGenerationRebuildTaskService service =
                new ArticleKnowledgeGenerationRebuildTaskService(rebuild, executor);
        when(rebuild.rebuild("g2")).thenThrow(new IllegalStateException("reconcile failed\nsecret omitted"));

        service.submit("g2");
        executor.runNext();

        assertEquals(ArticleKnowledgeGenerationRebuildTaskService.TaskStatus.FAILED,
                service.status().status());
        assertEquals("IllegalStateException: Generation重建失败，请查看服务端脱敏日志",
                service.status().errorSummary());
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            tasks.remove().run();
        }
    }
}
