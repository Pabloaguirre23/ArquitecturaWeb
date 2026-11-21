import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthProvider'
import { SearchPage } from './SearchPage'

const { searchEvents } = vi.hoisted(() => ({ searchEvents: vi.fn() }))

vi.mock('../api/events', () => ({
  searchEvents: (...args: unknown[]) => searchEvents(...args),
}))

;(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true

describe('SearchPage', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    root?.unmount()
    container?.remove()
    root = null
    container = null
    vi.clearAllMocks()
  })

  async function renderPage() {
    container = document.createElement('div')
    document.body.appendChild(container)
    const createdRoot = createRoot(container)
    root = createdRoot
    await act(async () => {
      createdRoot.render(
        <MemoryRouter>
          <AuthProvider>
            <SearchPage />
          </AuthProvider>
        </MemoryRouter>,
      )
    })
  }

  it('muestra los eventos que devuelve el catálogo y busca con los filtros elegidos', async () => {
    searchEvents.mockResolvedValue({
      content: [
        {
          id: 'd0000000-0000-4000-8000-000000000001',
          nombre: 'Festival de Rock Tandil 2027',
          tipo: 'RECITAL',
          ciudad: 'Tandil',
          lugarNombre: 'Estadio Municipal',
          fecha: '2027-03-14T21:00:00-03:00',
          estado: 'PUBLICADO',
          precioDesde: 25000,
        },
      ],
      page: 0,
      size: 6,
      totalElements: 1,
      totalPages: 1,
    })

    await renderPage()

    expect(container!.innerHTML).toContain('Festival de Rock Tandil 2027')
    expect(container!.innerHTML).toContain('1 evento para vos')
    expect(searchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 6, sort: 'FECHA_ASC' }),
    )

    const tipo = container!.querySelector('#tipo') as HTMLSelectElement
    const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!
    await act(async () => {
      setter.call(tipo, 'RECITAL')
      tipo.dispatchEvent(new Event('change', { bubbles: true }))
    })
    const form = container!.querySelector('form')!
    await act(async () => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    })

    expect(searchEvents).toHaveBeenLastCalledWith(
      expect.objectContaining({ tipo: 'RECITAL', page: 0 }),
    )
  })

  it('valida el rango de fechas antes de llamar al backend', async () => {
    searchEvents.mockResolvedValue({
      content: [],
      page: 0,
      size: 6,
      totalElements: 0,
      totalPages: 0,
    })
    await renderPage()

    const desde = container!.querySelector('#fechaDesde') as HTMLInputElement
    const hasta = container!.querySelector('#fechaHasta') as HTMLInputElement
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!
    await act(async () => {
      setter.call(desde, '2027-06-01')
      desde.dispatchEvent(new Event('input', { bubbles: true }))
      setter.call(hasta, '2027-05-01')
      hasta.dispatchEvent(new Event('input', { bubbles: true }))
    })
    const form = container!.querySelector('form')!
    const llamadas = searchEvents.mock.calls.length
    await act(async () => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    })

    expect(container!.innerHTML).toContain('no puede ser posterior')
    expect(searchEvents.mock.calls.length).toBe(llamadas)
  })
})
