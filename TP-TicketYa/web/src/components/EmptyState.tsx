import { Link } from 'react-router-dom'

export function EmptyState({
  title,
  description,
  actionLabel = 'Volver a la búsqueda',
  actionTo = '/',
  onAction,
}: {
  title: string
  description: string
  actionLabel?: string
  actionTo?: string
  onAction?: () => void
}) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      <p className="muted">{description}</p>
      {onAction ? (
        <button className="btn btn-primary" type="button" onClick={onAction}>
          {actionLabel}
        </button>
      ) : (
        <Link className="btn btn-primary" to={actionTo}>
          {actionLabel}
        </Link>
      )}
    </div>
  )
}
