import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  stopTask: vi.fn().mockResolvedValue(undefined)
}));

vi.mock("@/services/chatService", () => ({
  createChatStream: vi.fn(),
  stopTask: mocks.stopTask,
  submitFeedback: vi.fn()
}));

import { useChatStore } from "@/stores/chatStore";

describe("chatStore cancellation", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mocks.stopTask.mockClear();
    useChatStore.setState({
      messages: [],
      isStreaming: false,
      thinkingStartAt: null,
      streamTaskId: null,
      streamAbort: null,
      streamingMessageId: null,
      cancelRequested: false
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("服务端取消事件丢失时在预算后终止浏览器流并收敛消息状态", async () => {
    const abort = vi.fn();
    useChatStore.setState({
      messages: [
        {
          id: "assistant-1",
          role: "assistant",
          content: "部分回答",
          status: "streaming",
          isThinking: true
        }
      ],
      isStreaming: true,
      thinkingStartAt: Date.now(),
      streamTaskId: "task-1",
      streamAbort: abort,
      streamingMessageId: "assistant-1",
      cancelRequested: false
    });

    useChatStore.getState().cancelGeneration();
    expect(mocks.stopTask).toHaveBeenCalledWith("task-1");
    expect(useChatStore.getState().cancelRequested).toBe(true);

    await vi.advanceTimersByTimeAsync(3_000);

    expect(abort).toHaveBeenCalledOnce();
    expect(useChatStore.getState()).toMatchObject({
      isStreaming: false,
      streamTaskId: null,
      streamingMessageId: null,
      cancelRequested: false
    });
    expect(useChatStore.getState().messages[0]).toMatchObject({
      status: "cancelled",
      isThinking: false,
      content: "部分回答\n\n（已停止生成）"
    });
  });
});
