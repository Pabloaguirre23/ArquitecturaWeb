import { Link } from 'react-router-dom'
import type { SimilarEventItem } from '../api/types'
import { eventTypeLabel } from '../utils/eventTypes'
import { formatDate } from '../utils/format'

export function SimilarEventCard({ item }: { item: SimilarEventItem }) {
  return (
    <Link className="event-card" to={`/events/${item.eventoId}`}>
      <div className="event-cover">
        <span>{eventTypeLabel(item.tipo)}</span>
      </div>
      <div className="event-body">
        <h3>{item.nombre}</h3>
        <div className="meta">
          <span>{formatDate(item.fechaEvento)}</span>
          <span>·</span>
          <span>{item.ciudad}</span>
        </div>
        <p className="hint">
          {item.compradoresEnComun} persona{item.compradoresEnComun === 1 ? '' : 's'} que
          compraron este evento también compraron este.
        </p>
      </div>
    </Link>
  )
}
