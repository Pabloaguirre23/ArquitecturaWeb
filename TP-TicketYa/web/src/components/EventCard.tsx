import { Link } from 'react-router-dom'
import type { EventSummary } from '../api/types'
import { formatCurrency, formatDate } from '../utils/format'

const typeLabels: Record<string, string> = {
  RECITAL: 'Recital',
  TEATRO: 'Teatro',
  DEPORTE: 'Deporte',
  CONFERENCIA: 'Conferencia',
}

export function EventCard({ event }: { event: EventSummary }) {
  return (
    <Link className="event-card" to={`/events/${event.id}`}>
      <div className="event-cover">
        <span>{typeLabels[event.tipo] ?? event.tipo}</span>
      </div>
      <div className="event-body">
        <h3>{event.nombre}</h3>
        <div className="meta">
          <span>{formatDate(event.fecha)}</span>
          <span>·</span>
          <span>{event.lugarNombre}</span>
        </div>
        <div className="meta">
          <span>{event.ciudad}</span>
          {event.altaDemanda && <span className="badge">Alta demanda</span>}
        </div>
        <div className="price">
          {event.precioDesde === 0 ? 'Gratis' : `Desde ${formatCurrency(event.precioDesde)}`}
          <br />
          <small>Precio por entrada</small>
        </div>
      </div>
    </Link>
  )
}
