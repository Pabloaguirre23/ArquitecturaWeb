import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthProvider'
import { writeAuth } from '../auth/storage'
import { TicketsPage } from './TicketsPage'

vi.mock('../api/orders', () => ({
  getTickets: vi.fn().mockResolvedValue({
    content: [
      {
        id: '33333333-3333-4333-8333-333333333333',
        eventoId: 'd0000000-0000-4000-8000-000000000001',
        eventoNombre: 'Festival de Rock Tandil 2027',
        eventoFecha: '2027-03-14T21:00:00-03:00',
        sectorId: 'b0000000-0000-4000-8000-000000000001',
        asientoId: null,
        qrCode: '44444444-4444-4444-8444-444444444444',
        estado: 'VALIDA',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  }),
}))

;(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true

describe('TicketsPage', () => {
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

  it('muestra la entrada con su QR y el evento', async () => {
    writeAuth({
      token: 'test-token',
      user: { email: 'cliente@ticketya.test', role: 'CLIENT', nombre: 'Cliente Demo' },
    })

    container = document.createElement('div')
    document.body.appendChild(container)
    const createdRoot = createRoot(container)
    root = createdRoot

    await act(async () => {
      createdRoot.render(
        <MemoryRouter>
          <AuthProvider>
            <TicketsPage />
          </AuthProvider>
        </MemoryRouter>,
      )
    })
    await act(async () => {
      await Promise.resolve()
    })

    const html = container.innerHTML
    expect(html).toContain('Festival de Rock Tandil 2027')
    expect(html).toContain('44444444-4444-4444-8444-444444444444')
    expect(html).toContain('VALIDA')
  })

  it('oculta la vista para roles que no son CLIENT', async () => {
    writeAuth({
      token: 'test-token',
      user: { email: 'acceso@ticketya.test', role: 'ACCESS_STAFF', nombre: 'Acceso' },
    })

    container = document.createElement('div')
    document.body.appendChild(container)
    const rootCreated = createRoot(container)
    root = rootCreated

    await act(async () => {
      rootCreated.render(
        <MemoryRouter>
          <AuthProvider>
            <TicketsPage />
          </AuthProvider>
        </MemoryRouter>,
      )
    })

    expect(container.innerHTML).toContain('Solo para clientes')
  })
})
