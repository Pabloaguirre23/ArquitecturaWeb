import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <span className="badge">404</span>
        <h1>Esta página no existe</h1>
        <p>El enlace puede estar roto o el evento ya no está disponible.</p>
        <Link className="btn btn-primary" to="/">
          Volver a la búsqueda
        </Link>
      </div>
    </div>
  )
}
