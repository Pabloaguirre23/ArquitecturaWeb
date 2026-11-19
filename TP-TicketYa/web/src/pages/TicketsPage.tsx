import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getTickets } from '../api/orders'
import type { Ticket } from '../api/types'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { Loading } from '../components/Loading'
import { QrPlaceholder } from '../components/QrPlaceholder'
import { errorMessage } from '../utils/errors'
import { formatDate } from '../utils/format'

export function TicketsPage() {
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    getTickets()
      .then((result) => {
        if (!cancelled) {
          setTickets(result.content)
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
  }, [])

  return (
    <div className="page-gap">
      <header className="page-head">
        <span className="badge">Mi cuenta</span>
        <h1>Mis entradas</h1>
        <p className="muted">Presentá el QR en el ingreso del evento.</p>
      </header>

      {loading && <Loading label="Buscando tus entradas…" />}

      {!loading && error && (
        <ErrorState message={error} onRetry={() => window.location.reload()} />
      )}

      {!loading && !error && tickets.length === 0 && (
        <EmptyState
          title="Todavía no tenés entradas"
          description="Cuando compres una entrada, va a aparecer acá con su código QR."
          actionLabel="Buscar eventos"
        />
      )}

      {!loading && !error && tickets.length > 0 && (
        <div className="ticket-list">
          {tickets.map((ticket) => (
            <article className="ticket-card" key={ticket.id}>
              <QrPlaceholder value={ticket.qrCode} />
              <div className="ticket-info">
                <span className={ticket.estado === 'VALIDA' ? 'badge ok' : 'badge bad'}>
                  {ticket.estado}
                </span>
                <h3>{ticket.eventoNombre}</h3>
                <p className="muted">{formatDate(ticket.eventoFecha)}</p>
                <p className="meta">
                  <span>Sector {ticket.sectorId.slice(0, 8)}</span>
                  {ticket.asientoId && <span>Asiento {ticket.asientoId.slice(0, 8)}</span>}
                </p>
                <p className="mono qr-code">{ticket.qrCode}</p>
              </div>
            </article>
          ))}
        </div>
      )}

      <p className="hint">
        ¿Querés más entradas? <Link to="/">Volver a la búsqueda</Link>
      </p>
    </div>
  )
}
