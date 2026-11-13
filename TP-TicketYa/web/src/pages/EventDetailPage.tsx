import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getEvent, getSeats } from '../api/events'
import { createReservation } from '../api/orders'
import type {
  CreateReservationItem,
  EventDetail,
  Seat,
  Sector,
} from '../api/types'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { Loading } from '../components/Loading'
import { SeatGrid } from '../components/SeatGrid'
import { errorMessage } from '../utils/errors'
import { formatCurrency, formatDate } from '../utils/format'
import { saveReservation } from '../utils/reservationStorage'

export function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [event, setEvent] = useState<EventDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [sectorId, setSectorId] = useState<string | null>(null)
  const [seats, setSeats] = useState<Seat[]>([])
  const [seatsLoading, setSeatsLoading] = useState(false)
  const [selectedSeats, setSelectedSeats] = useState<Set<string>>(new Set())
  const [quantity, setQuantity] = useState(1)
  const [reserving, setReserving] = useState(false)
  const [reserveError, setReserveError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) {
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)

    getEvent(id)
      .then((result) => {
        if (!cancelled) {
          setEvent(result)
          setSectorId(result.sectores[0]?.sectorId ?? null)
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(errorMessage(caught))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [id])

  const selectedSector = useMemo(
    () => event?.sectores.find((sector) => sector.sectorId === sectorId) ?? null,
    [event, sectorId],
  )

  useEffect(() => {
    if (!event || !selectedSector || selectedSector.tipo !== 'NUMERADO') {
      setSeats([])
      setSeatsLoading(false)
      return
    }

    let cancelled = false
    setSeatsLoading(true)
    setSelectedSeats(new Set())
    setQuantity(1)

    getSeats(event.id, selectedSector.sectorId)
      .then((result) => {
        if (!cancelled) {
          setSeats(result)
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setReserveError(errorMessage(caught))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setSeatsLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [event, selectedSector])

  function selectSector(sector: Sector) {
    if (sector.disponibles <= 0) {
      return
    }
    setSectorId(sector.sectorId)
    setSelectedSeats(new Set())
    setQuantity(1)
    setReserveError(null)
  }

  function toggleSeat(seat: Seat) {
    if (!event || seat.estado !== 'LIBRE') {
      return
    }
    setSelectedSeats((current) => {
      const next = new Set(current)
      if (next.has(seat.asientoId)) {
        next.delete(seat.asientoId)
      } else if (next.size < event.limitePorCliente) {
        next.add(seat.asientoId)
      }
      return next
    })
  }

  const total = useMemo(() => {
    if (!selectedSector) {
      return 0
    }
    if (selectedSector.tipo === 'NUMERADO') {
      return selectedSeats.size * selectedSector.precio
    }
    return quantity * selectedSector.precio
  }, [selectedSector, selectedSeats, quantity])

  const canContinue =
    Boolean(selectedSector) &&
    (selectedSector?.tipo === 'NUMERADO' ? selectedSeats.size > 0 : quantity > 0)

  async function handleContinue() {
    if (!event || !selectedSector) {
      return
    }

    const items: CreateReservationItem[] =
      selectedSector.tipo === 'NUMERADO'
        ? [{ sectorId: selectedSector.sectorId, asientoIds: Array.from(selectedSeats) }]
        : [{ sectorId: selectedSector.sectorId, cantidad: quantity }]

    setReserving(true)
    setReserveError(null)
    try {
      const reservation = await createReservation(event.id, items)
      saveReservation(reservation)
      navigate('/checkout')
    } catch (caught) {
      setReserveError(errorMessage(caught))
    } finally {
      setReserving(false)
    }
  }

  if (loading) {
    return <Loading label="Cargando evento…" />
  }

  if (error) {
    return (
      <div className="section-gap">
        <ErrorState message={error} onRetry={() => window.location.reload()} />
      </div>
    )
  }

  if (!event) {
    return (
      <EmptyState
        title="Evento no encontrado"
        description="Puede que ya no esté publicado o que el enlace sea incorrecto."
      />
    )
  }

  const maxQuantity = selectedSector
    ? Math.min(selectedSector.disponibles, event.limitePorCliente)
    : 1

  return (
    <div className="page-gap">
      <Link className="back-link" to="/">
        ← Volver a la búsqueda
      </Link>

      <header className="page-head">
        <span className="badge">{event.tipo}</span>
        <h1>{event.nombre}</h1>
        <p className="muted">{event.descripcion}</p>
        <div className="meta">
          <span>{formatDate(event.fecha)}</span>
          <span>·</span>
          <span>{event.lugarNombre}</span>
          <span>·</span>
          <span>{event.ciudad}</span>
        </div>
        <div className="meta">
          <span>Participan: {event.participantes.join(', ')}</span>
        </div>
      </header>

      <div className="detail-grid">
        <section className="card">
          <h2>Elegí tu lugar</h2>
          <p className="muted">Límite de {event.limitePorCliente} entradas por cliente.</p>

          <div className="sector-list">
            {event.sectores.map((sector) => (
              <button
                key={sector.sectorId}
                className={`sector ${sector.sectorId === selectedSector?.sectorId ? 'selected' : ''}`}
                type="button"
                onClick={() => selectSector(sector)}
                disabled={sector.disponibles <= 0}
              >
                <span>
                  <strong>{sector.nombre}</strong>
                  <small>
                    {sector.tipo === 'NUMERADO'
                      ? `${sector.disponibles} de ${sector.capacidad} asientos disponibles`
                      : `${sector.disponibles} lugares disponibles`}
                  </small>
                </span>
                <span className="price">
                  {sector.precio === 0 ? 'Gratis' : formatCurrency(sector.precio)}
                </span>
              </button>
            ))}
          </div>

          {selectedSector?.tipo === 'NUMERADO' && (
            <div className="seat-picker">
              {seatsLoading ? (
                <Loading label="Cargando asientos…" />
              ) : seats.length === 0 ? (
                <p className="muted">No hay asientos disponibles en este sector.</p>
              ) : (
                <SeatGrid
                  seats={seats}
                  selected={selectedSeats}
                  onToggle={toggleSeat}
                  maxSelectable={event.limitePorCliente}
                />
              )}
            </div>
          )}

          {selectedSector?.tipo === 'NO_NUMERADO' && (
            <div className="zone-picker">
              <label htmlFor="quantity">Cantidad de lugares</label>
              <input
                id="quantity"
                type="number"
                min={1}
                max={maxQuantity}
                value={quantity}
                onChange={(event) => {
                  const next = Number(event.target.value)
                  setQuantity(Number.isFinite(next) ? Math.min(Math.max(next, 1), maxQuantity) : 1)
                }}
              />
              <span className="muted">Máximo {maxQuantity} por compra.</span>
            </div>
          )}
        </section>

        <aside className="card summary">
          <h2>Resumen</h2>
          {selectedSector ? (
            <>
              <div className="summary-row">
                <span>Sector</span>
                <strong>{selectedSector.nombre}</strong>
              </div>
              <div className="summary-row">
                <span>{selectedSector.tipo === 'NUMERADO' ? 'Asientos' : 'Cantidad'}</span>
                <strong>
                  {selectedSector.tipo === 'NUMERADO' ? selectedSeats.size : quantity}
                </strong>
              </div>
              {selectedSector.tipo === 'NUMERADO' && selectedSeats.size > 0 && (
                <div className="summary-row">
                  <span>Elegidos</span>
                  <span className="muted">
                    {Array.from(selectedSeats).length} asiento(s)
                  </span>
                </div>
              )}
              <div className="summary-row summary-total">
                <span>Total estimado</span>
                <span>{formatCurrency(total)}</span>
              </div>
              {reserveError && (
                <p className="form-error" role="alert">
                  {reserveError}
                </p>
              )}
              <button
                className="btn btn-primary"
                type="button"
                disabled={!canContinue || reserving}
                onClick={handleContinue}
              >
                {reserving ? 'Reservando…' : 'Continuar al pago'}
              </button>
              <p className="hint">Los lugares se reservan por 10 minutos mientras pagás.</p>
            </>
          ) : (
            <p className="muted">Elegí un sector para ver el total.</p>
          )}
        </aside>
      </div>
    </div>
  )
}
