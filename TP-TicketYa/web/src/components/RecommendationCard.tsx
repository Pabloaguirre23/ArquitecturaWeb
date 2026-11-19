import { Link } from 'react-router-dom'
import type { RecommendationItem } from '../api/types'
import { eventTypeLabel } from '../utils/eventTypes'
import { formatDate } from '../utils/format'

export function RecommendationCard({ item }: { item: RecommendationItem }) {
  return (
    <Link className="event-card recommendation-card" to={`/events/${item.eventoId}`}>
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
        <ul className="reason-list">
          {item.motivos.map((motivo) => (
            <li key={motivo}>{motivo}</li>
          ))}
        </ul>
        <div className="price">
          Afinidad {item.puntaje}
          {item.altaDemanda && <span className="badge">Alta demanda</span>}
        </div>
      </div>
    </Link>
  )
}
