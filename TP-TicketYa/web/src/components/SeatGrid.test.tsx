import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { Seat } from '../api/types'
import { SeatGrid } from './SeatGrid'

const seats: Seat[] = [
  { asientoId: 'a1', fila: 1, numero: 1, estado: 'LIBRE' },
  { asientoId: 'a2', fila: 1, numero: 2, estado: 'VENDIDO' },
]

describe('SeatGrid', () => {
  it('expone el grupo, el estado de cada asiento y el foco por teclado', () => {
    const html = renderToStaticMarkup(
      <SeatGrid seats={seats} selected={new Set(['a1'])} onToggle={() => undefined} maxSelectable={4} />,
    )

    expect(html).toContain('role="group"')
    expect(html).toContain('aria-label="Mapa de asientos"')
    expect(html).toContain('aria-pressed="true"')
    expect(html).toContain('aria-label="Fila 1, asiento 1, libre"')
    expect(html).toContain('aria-label="Fila 1, asiento 2, vendido"')
    expect(html).toContain('disabled=""')
    expect(html).toContain('aria-hidden="true"')
  })
})
