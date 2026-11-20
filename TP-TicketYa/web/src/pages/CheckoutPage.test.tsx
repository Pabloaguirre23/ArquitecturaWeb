import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthProvider'
import { writeAuth } from '../auth/storage'
import { CheckoutPage } from './CheckoutPage'
import { saveReservation } from '../utils/reservationStorage'

// La Entrada embebida de OrderResponse (orders-service) NO trae eventoNombre ni
// eventoFecha: esos campos viven en la orden. Este mock replica esa forma real
// para comprobar que la pantalla no depende de campos inexistentes.
vi.mock('../api/orders', () => ({
  createOrder: vi.fn().mockResolvedValue({
    id: '11111111-1111-4111-8111-111111111111',
    clienteId: '22222222-2222-4222-8222-222222222222',
    reservaId: 'reserva-1',
    eventoId: 'd0000000-0000-4000-8000-000000000001',
    eventoNombre: 'Festival de Rock Tandil 2027',
    eventoFecha: '2027-03-14T21:00:00-03:00',
    estado: 'PAGADA',
    medioPago: 'TARJETA',
    total: 25000,
    items: [
      {
        sectorId: 'b0000000-0000-4000-8000-000000000001',
        asientoId: null,
        cantidad: 1,
        precioUnitario: 25000,
        subtotal: 25000,
      },
    ],
    entradas: [
      {
        id: '33333333-3333-4333-8333-333333333333',
        qrCode: '44444444-4444-4444-8444-444444444444',
        estado: 'VALIDA',
        sectorId: 'b0000000-0000-4000-8000-000000000001',
        asientoId: null,
      },
    ],
    createdAt: '2026-11-19T12:00:00-03:00',
  }),
}))

;(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true

describe('CheckoutPage', () => {
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

  it('muestra el evento y el QR de la orden con la forma real del backend', async () => {
    writeAuth({
      token: 'test-token',
      user: {
        email: 'cliente@ticketya.test',
        role: 'CLIENT',
        nombre: 'Cliente Demo',
      },
    })
    saveReservation({
      id: 'reserva-1',
      eventoId: 'd0000000-0000-4000-8000-000000000001',
      eventoNombre: 'Festival de Rock Tandil 2027',
      eventoFecha: '2027-03-14T21:00:00-03:00',
      clienteId: '22222222-2222-4222-8222-222222222222',
      estado: 'VIGENTE',
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      items: [
        {
          sectorId: 'b0000000-0000-4000-8000-000000000001',
          cantidad: 1,
          precioUnitario: 25000,
          subtotal: 25000,
        },
      ],
      total: 25000,
    })

    container = document.createElement('div')
    document.body.appendChild(container)
    const createdRoot = createRoot(container)
    root = createdRoot

    await act(async () => {
      createdRoot.render(
        <MemoryRouter>
          <AuthProvider>
            <CheckoutPage />
          </AuthProvider>
        </MemoryRouter>,
      )
    })

    const form = container.querySelector('form')
    expect(form).not.toBeNull()

    await act(async () => {
      form!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    })

    const html = container.innerHTML
    expect(html).toContain('¡Compra confirmada!')
    expect(html).toContain('Festival de Rock Tandil 2027')
    expect(html).toContain('44444444-4444-4444-8444-444444444444')
    expect(html).not.toContain('Invalid Date')
  })
})
