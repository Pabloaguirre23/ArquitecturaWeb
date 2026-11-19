import { API_BASE_URL } from '../config'
import { readAuth } from '../auth/storage'
import type { ProblemDetail } from './types'

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly details: string[]

  constructor(status: number, message: string, code?: string, details: string[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 12000)
  const headers = new Headers(options.headers)

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const token = readAuth()?.token
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
      signal: controller.signal,
    })

    if (response.status === 401) {
      window.dispatchEvent(new Event('ticketya:unauthorized'))
    }

    if (!response.ok) {
      let problem: ProblemDetail | null = null
      try {
        problem = (await response.json()) as ProblemDetail
      } catch {
        problem = null
      }
      throw new ApiError(
        response.status,
        problem?.detail ?? `El servidor respondió ${response.status}`,
        problem?.code,
        problem?.errors ?? [],
      )
    }

    if (response.status === 204) {
      return undefined as T
    }

    return (await response.json()) as T
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError(408, 'La solicitud tardó demasiado. Probá de nuevo.', 'TIMEOUT')
    }
    throw new ApiError(0, 'No se pudo conectar con el servidor. Revisá que el gateway esté levantado.', 'NETWORK_ERROR')
  } finally {
    window.clearTimeout(timeout)
  }
}

export function get<T>(path: string): Promise<T> {
  return request<T>(path)
}

export function post<T>(path: string, body: unknown, headers?: Record<string, string>): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    body: JSON.stringify(body),
    headers,
  })
}

export function put<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}
