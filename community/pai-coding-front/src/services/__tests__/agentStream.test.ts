import { describe, expect, it } from 'vitest'
import { parseSseFrames } from '../agent'

describe('Agent SSE parser', () => {
  it('解析跨块、多行 data，并保留未完成帧', () => {
    const first = parseSseFrames(
      'event: accepted\r\ndata: {"requestId":"req-1"}\r\n\r\n' +
        'event: delta\ndata: {"requestId":"req-1",'
    )

    expect(first.events).toEqual([
      {
        event: 'accepted',
        data: '{"requestId":"req-1"}'
      }
    ])
    expect(first.remainder).toBe(
      'event: delta\ndata: {"requestId":"req-1",'
    )

    const second = parseSseFrames(
      `${first.remainder}"text":"可靠"}\n\n` +
        'event: status\ndata: {"phase":"GENERATING",\n' +
        'data: "requestId":"req-1"}\n\n'
    )

    expect(second.events).toEqual([
      {
        event: 'delta',
        data: '{"requestId":"req-1","text":"可靠"}'
      },
      {
        event: 'status',
        data: '{"phase":"GENERATING",\n"requestId":"req-1"}'
      }
    ])
    expect(second.remainder).toBe('')
  })

  it('忽略注释和没有 data 的空帧', () => {
    const parsed = parseSseFrames(': heartbeat\n\nevent: status\n\n')
    expect(parsed.events).toEqual([])
    expect(parsed.remainder).toBe('')
  })
})
