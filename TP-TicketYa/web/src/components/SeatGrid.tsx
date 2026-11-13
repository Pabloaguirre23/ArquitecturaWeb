import { useMemo } from 'react'
import type { Seat } from '../api/types'

export function SeatGrid({
  seats,
  selected,
  onToggle,
  maxSelectable,
}: {
  seats: Seat[]
  selected: Set<string>
  onToggle: (seat: Seat) => void
  maxSelectable: number
}) {
  const rows = useMemo(() => {
    const grouped = new Map<number, Seat[]>()
    for (const seat of seats) {
      const row = grouped.get(seat.fila) ?? []
      row.push(seat)
      grouped.set(seat.fila, row)
    }
    return Array.from(grouped.entries()).sort(([a], [b]) => a - b)
  }, [seats])

  return (
    <div className="seat-map" role="group" aria-label="Mapa de asientos">
      <div className="seat-legend">
        <span><i className="legend-dot free" /> Libre</span>
        <span><i className="legend-dot selected" /> Elegido</span>
        <span><i className="legend-dot taken" /> Ocupado</span>
      </div>
      {rows.map(([fila, rowSeats]) => (
        <div className="seat-row" key={fila}>
          <span className="seat-row-label">{fila}</span>
          {rowSeats.map((seat) => {
            const isSelected = selected.has(seat.asientoId)
            const disabled = seat.estado !== 'LIBRE' || (!isSelected && selected.size >= maxSelectable)
            return (
              <button
                key={seat.asientoId}
                className={`seat ${isSelected ? 'selected' : ''}`}
                type="button"
                aria-pressed={isSelected}
                aria-label={`Fila ${seat.fila}, asiento ${seat.numero}, ${seat.estado.toLowerCase()}`}
                title={`Fila ${seat.fila}, asiento ${seat.numero} · ${seat.estado}`}
                disabled={disabled}
                onClick={() => onToggle(seat)}
              >
                {seat.numero}
              </button>
            )
          })}
        </div>
      ))}
    </div>
  )
}
