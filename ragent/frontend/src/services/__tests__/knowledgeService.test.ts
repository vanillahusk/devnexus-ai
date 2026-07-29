import { beforeEach, describe, expect, it, vi } from "vitest";

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  put: vi.fn(),
  delete: vi.fn()
}));

vi.mock("../api", () => ({
  api: apiMock
}));

import {
  batchDisableChunks,
  enableDocument,
  getDocumentsPage,
  rebuildChunks,
  startDocumentChunk
} from "../knowledgeService";

describe("knowledgeService 管理操作契约", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("文档分页只发送有效筛选条件", async () => {
    const page = {
      records: [],
      total: 0,
      size: 20,
      current: 2,
      pages: 0
    };
    apiMock.get.mockResolvedValue(page);

    await expect(
      getDocumentsPage("kb-1", {
        current: 2,
        size: 20,
        status: "",
        keyword: "outbox"
      })
    ).resolves.toEqual(page);

    expect(apiMock.get).toHaveBeenCalledWith("/knowledge-base/kb-1/docs", {
      params: {
        current: 2,
        size: 20,
        status: undefined,
        keyword: "outbox"
      }
    });
  });

  it("启停文档使用显式布尔参数", async () => {
    apiMock.patch.mockResolvedValue(undefined);

    await enableDocument("doc-1", false);

    expect(apiMock.patch).toHaveBeenCalledWith(
      "/knowledge-base/docs/doc-1/enable",
      null,
      { params: { value: false } }
    );
  });

  it("分块和重建使用独立管理端点", async () => {
    apiMock.post.mockResolvedValue(undefined);

    await startDocumentChunk("doc-1");
    await rebuildChunks("doc-1");

    expect(apiMock.post).toHaveBeenNthCalledWith(
      1,
      "/knowledge-base/docs/doc-1/chunk"
    );
    expect(apiMock.post).toHaveBeenNthCalledWith(
      2,
      "/knowledge-base/docs/doc-1/chunks/rebuild"
    );
  });

  it("批量禁用不把空数组解释为指定 Chunk", async () => {
    apiMock.post.mockResolvedValue(undefined);

    await batchDisableChunks("doc-1", []);

    expect(apiMock.post).toHaveBeenCalledWith(
      "/knowledge-base/docs/doc-1/chunks/batch-disable",
      { chunkIds: undefined }
    );
  });
});
