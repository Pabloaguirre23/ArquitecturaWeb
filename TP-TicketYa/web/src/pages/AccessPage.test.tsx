import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthProvider'
import { writeAuth } from '../auth/storage'
import { AccessPage } from './AccessPage'

vi.mock('../api/staff', () => ({
  validateAccess: vi.fn().mockResolvedValue({
    entradaId: '33333333-3333-4333-8333-333333333333',
    ordenId: '11111111-1111-4111-8111-111111111111',
    eventoId: 'd0000000-0000-4000-8000-000000000001',
    sectorId: 'b0000000-0000-4000-8000-000000000001',
    estado: 'UTILIZADA',
    usedAt: '2027-03-14T23:10:00-03:00',
    usedBy: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  }),
}))

;(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true

describe('AccessPage', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    root?.unmount()
    container?.remove()
    root = null
    container = null
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('valida un QR y muestra el resultado del backend', async () => {
    writeAuth({
      token: 'test-token',
      user: { email: 'acceso@ticketya.test', role: 'ACCESS_STAFF', nombre: 'Acceso' },
    })

    container = document.createElement('div')
    document.body.appendChild(container)
    const createdRoot = createRoot(container)
    root = createdRoot

    await act(async () => {
      createdRoot.render(
        <MemoryRouter>
          <AuthProvider>
            <AccessPage />
          </AuthProvider>
        </MemoryRouter>,
      )
    })

    const qr = container.querySelector('#qr-code') as HTMLInputElement
    const evento = container.querySelector('#evento-id') as HTMLInputElement
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!
    await act(async () => {
      setter.call(qr, '44444444-4444-4444-8444-444444444444')
      qr.dispatchEvent(new Event('input', { bubbles: true }))
      setter.call(evento, 'd0000000-0000-4000-8000-000000000001')
      evento.dispatchEvent(new Event('input', { bubbles: true }))
    })
    await act(async () => {
      container!.querySelector('form')!.dispatchEvent(
        new Event('submit', { bubbles: true, cancelable: true }),
      )
    })

    const html = container.innerHTML
    expect(html).toContain('Ingreso válido')
    expect(html).toContain('UTILIZADA')
  })

  it('niega la pantalla a un cliente', async () => {
    writeAuth({
      token: 'test-token',
      user: { email: 'cliente@ticketya.test', role: 'CLIENT', nombre: 'Cliente' },
    })

    container = document.createElement('div')
    document.body.appendChild(container)
    const rootCreated = createRoot(container)
    root = rootCreated

    await act(async () => {
      rootCreated.render(
        <MemoryRouter>
          <AuthProvider>
            <AccessPage />
          </AuthProvider>
        </MemoryRouter>,
      )
    })

    expect(container.innerHTML).toContain('Solo personal de acceso')
  })
})
