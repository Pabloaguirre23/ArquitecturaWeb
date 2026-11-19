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

  it('no emite ticketya:unauthorized cuando falla el login (credenciales inválidas)', async () => {
    const unauthorized = vi.fn()
    window.addEventListener('ticketya:unauthorized', unauthorized)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            title: 'Unauthorized',
            status: 401,
            detail: 'Email o contraseña incorrectos',
            code: 'UNAUTHORIZED',
            errors: [],
          }),
          {
            status: 401,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    )

    await expect(get('/auth/login')).rejects.toMatchObject({ status: 401 })
    expect(unauthorized).not.toHaveBeenCalled()
    window.removeEventListener('ticketya:unauthorized', unauthorized)
  })

  it('emite ticketya:unauthorized cuando expira la sesión en una ruta protegida', async () => {
    const unauthorized = vi.fn()
    window.addEventListener('ticketya:unauthorized', unauthorized)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            title: 'Unauthorized',
            status: 401,
            detail: 'El token expiró',
            code: 'UNAUTHORIZED',
            errors: [{ field: 'token', message: 'vencido' }],
          }),
          {
            status: 401,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    )

    await expect(get('/orders/tickets')).rejects.toMatchObject({ status: 401 })
    expect(unauthorized).toHaveBeenCalledTimes(1)
    window.removeEventListener('ticketya:unauthorized', unauthorized)
  })
})
