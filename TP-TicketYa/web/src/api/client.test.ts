import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, get } from './client'

afterEach(() => {
  vi.unstubAllGlobals()
  sessionStorage.clear()
})

describe('api client', () => {
  it('parsea una respuesta 200', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await expect(get<{ ok: boolean }>('/ping')).resolves.toEqual({ ok: true })
  })

  it('convierte un problem+json en ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            title: 'Unauthorized',
            status: 401,
            detail: 'El token es inválido',
            code: 'UNAUTHORIZED',
          }),
          {
            status: 401,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    )

    await expect(get('/private')).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'El token es inválido',
    })
  })

  it('devuelve NETWORK_ERROR si fetch falla', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('failed')))

    await expect(get('/ping')).rejects.toBeInstanceOf(ApiError)
    await expect(get('/ping')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })
})
