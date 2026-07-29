import { afterEach, describe, expect, it, vi } from "vitest";

import { createStreamResponse } from "@/hooks/useStreamResponse";

function sseResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    }
  });
  return new Response(stream, {
    status: 200,
    headers: { "Content-Type": "text/event-stream" }
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("createStreamResponse", () => {
  it("跨网络分片解析 meta、message 与 done 事件", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      sseResponse([
        'event: meta\ndata: {"conversationId":"c1",',
        '"taskId":"t1"}\n\nevent: message\n',
        'data: {"type":"response","delta":"可靠回答"}\n\nevent: done\ndata: {}\n\n'
      ])
    );
    vi.stubGlobal("fetch", fetchMock);
    const onMeta = vi.fn();
    const onMessage = vi.fn();
    const onDone = vi.fn();

    const stream = createStreamResponse(
      { url: "/rag/v3/chat", retryCount: 0 },
      { onMeta, onMessage, onDone }
    );
    await stream.start();

    expect(onMeta).toHaveBeenCalledWith({
      conversationId: "c1",
      taskId: "t1"
    });
    expect(onMessage).toHaveBeenCalledWith({
      type: "response",
      delta: "可靠回答"
    });
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("可重试错误只在预算内重试", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockResolvedValueOnce(sseResponse(["event: done\ndata: {}\n\n"]));
    vi.stubGlobal("fetch", fetchMock);
    const onDone = vi.fn();

    const stream = createStreamResponse(
      {
        url: "/rag/v3/chat",
        retryCount: 1,
        retryDelayMs: 0
      },
      { onDone }
    );
    await stream.start();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("取消时中止正在进行的浏览器请求", async () => {
    const fetchMock = vi.fn(
      (_url: string | URL | Request, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => {
            reject(new DOMException("aborted", "AbortError"));
          });
        })
    );
    vi.stubGlobal("fetch", fetchMock);

    const stream = createStreamResponse(
      { url: "/rag/v3/chat", retryCount: 0 },
      {}
    );
    const pending = stream.start();
    stream.cancel();

    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
  });
});
