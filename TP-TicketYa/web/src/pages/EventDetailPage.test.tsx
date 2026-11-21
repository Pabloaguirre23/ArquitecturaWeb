import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthProvider'
import { writeAuth } from '../auth/storage'
import { EventDetailPage } from './EventDetailPage'

const { getEvent, getSeats } = vi.hoisted(() => ({
  getEvent: vi.fn(),
  getSeats: vi.fn(),
}))
const { getSimilarEvents } = vi.hoisted(() => ({ getSimilarEvents: vi.fn() }))
const { createReservation } = vi.hoisted(() => ({ createReservation: vi.fn() }))

vi.mock('../api/events', () => ({ getEvent, getSeats }))
vi.mock('../api/recommendations', () => ({ getSimilarEvents }))
vi.mock('../api/orders', () => ({ createReservation }))

;(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true

const EVENTO = 'd0000000-0000-4000-8000-000000000001'

describe('EventDetailPage', () => {
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

  it('muestra el evento, calcula el total y reserva la cantidad elegida', async () => {
    writeAuth({
      token: 'test-token',
      user: { email: 'cliente@ticketya.test', role: 'CLIENT', nombre: 'Cliente Demo' },
    })
    getEvent.mockResolvedValue({
      id: EVENTO,
      nombre: 'Festival de Rock Tandil 2027',
      descripcion: 'Una noche de rock.',
      tipo: 'RECITAL',
      lugarId: 'a0000000-0000-4000-8000-000000000001',
      lugarNombre: 'Estadio Municipal',
      ciudad: 'Tandil',
      organizadorId: '22222222-2222-4222-8222-222222222222',
      fecha: '2027-03-14T21:00:00-03:00',
      estado: 'PUBLICADO',
      limitePorCliente: 4,
      participantes: ['Banda Demo'],
      sectores: [
        {
          sectorId: 'b0000000-0000-4000-8000-000000000001',
          nombre: 'Campo',
          tipo: 'NO_NUMERADO',
          precio: 25000,
          capacidad: 100,
          disponibles: 5,
        },
      ],
    })
    getSimilarEvents.mockResolvedValue({ eventoId: EVENTO, similares: [] })
    createReservation.mockResolvedValue({
      id: 'reserva-1',
      eventoId: EVENTO,
      eventoNombre: 'Festival de Rock Tandil 2027',
      eventoFecha: '2027-03-14T21:00:00-03:00',
      clienteId: '33333333-3333-4333-8333-333333333333',
      estado: 'VIGENTE',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      items: [],
      total: 75000,
    })

    container = document.createElement('div')
    document.body.appendChild(container)
    const createdRoot = createRoot(container)
    root = createdRoot
    await act(async () => {
      createdRoot.render(
        <MemoryRouter initialEntries={[`/events/${EVENTO}`]}>
          <AuthProvider>
            <Routes>
              <Route path="/events/:id" element={<EventDetailPage />} />
              <Route path="/checkout" element={<p>Checkout</p>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>,
      )
    })
    await act(async () => {})

    expect(container.innerHTML).toContain('Festival de Rock Tandil 2027')
    expect(container.innerHTML).toContain('Campo')
    expect(container.innerHTML).toContain('$')
    expect(container.innerHTML).toContain('25.000')

    const cantidad = container.querySelector('#quantity') as HTMLInputElement
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!
    await act(async () => {
      setter.call(cantidad, '3')
      cantidad.dispatchEvent(new Event('input', { bubbles: true }))
    })

    expect(container.innerHTML).toContain('75.000')

    const continuar = Array.from(container.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Continuar al pago'),
    )!
    await act(async () => {
      continuar.click()
    })

    expect(createReservation).toHaveBeenCalledWith(EVENTO, [
      { sectorId: 'b0000000-0000-4000-8000-000000000001', cantidad: 3 },
    ])
  })
})
