import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { USE_MOCKS } from '../config'
import { DemoBanner } from './DemoBanner'

export function Layout() {
  const { user, isAuthenticated, logout } = useAuth()

  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="container header-inner">
          <Link className="brand" to="/">
            <span className="brand-mark" aria-hidden="true">
              T
            </span>
            <span>TicketYa</span>
          </Link>

          <nav className="nav" aria-label="Navegación principal">
            <NavLink to="/" end>
              Buscar
            </NavLink>
            {isAuthenticated && <NavLink to="/tickets">Mis entradas</NavLink>}
          </nav>

          <div className="header-actions">
            {isAuthenticated ? (
              <>
                <span className="user-chip" title={user?.email}>
                  {user?.nombre ?? user?.email}
                </span>
                <button className="btn btn-ghost btn-small" type="button" onClick={logout}>
                  Salir
                </button>
              </>
            ) : (
              <Link className="btn btn-primary btn-small" to="/login">
                Ingresar
              </Link>
            )}
          </div>
        </div>
      </header>

      {USE_MOCKS && <DemoBanner />}

      <main className="container main-content">
        <Outlet />
      </main>

      <footer className="site-footer">
        <div className="container footer-inner">
          <span>TicketYa · TP Arquitectura Web · UNICEN 2026</span>
          <span className="muted">Demo de interfaz del cliente</span>
        </div>
      </footer>
    </div>
  )
}
