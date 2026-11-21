import { Link, NavLink, Outlet } from 'react-router-dom'
import { homeForRole, roleLabel } from '../auth/roles'
import { useAuth } from '../auth/useAuth'
import { USE_MOCKS } from '../config'
import { DemoBanner } from './DemoBanner'

export function Layout() {
  const { user, isAuthenticated, logout } = useAuth()

  function closeMenu() {
    const menu = document.getElementById('nav-mobile')
    if (menu instanceof HTMLElement && menu.matches(':popover-open')) {
      menu.hidePopover()
    }
  }

  const links = (
    <>
      {(!isAuthenticated || user?.role === 'CLIENT') && (
        <NavLink to="/" end onClick={closeMenu}>
          Buscar
        </NavLink>
      )}
      {isAuthenticated && user?.role === 'CLIENT' && (
        <NavLink to="/para-vos" onClick={closeMenu}>
          Para vos
        </NavLink>
      )}
      {isAuthenticated && user?.role === 'CLIENT' && (
        <NavLink to="/tickets" onClick={closeMenu}>
          Mis entradas
        </NavLink>
      )}
      {isAuthenticated && (user?.role === 'ACCESS_STAFF' || user?.role === 'ADMIN') && (
        <NavLink to="/access" onClick={closeMenu}>
          Control de acceso
        </NavLink>
      )}
      {isAuthenticated && (user?.role === 'ORGANIZER' || user?.role === 'ADMIN') && (
        <NavLink to="/reports" onClick={closeMenu}>
          Reportes
        </NavLink>
      )}
    </>
  )

  const actions = isAuthenticated ? (
    <>
      <span className="user-chip" title={user?.email}>
        {user?.nombre ?? user?.email}
      </span>
      <span className="role-chip">{roleLabel(user?.role)}</span>
      <button
        className="btn btn-ghost btn-small"
        type="button"
        onClick={() => {
          closeMenu()
          logout()
        }}
      >
        Salir
      </button>
    </>
  ) : (
    <Link className="btn btn-primary btn-small" to="/login" onClick={closeMenu}>
      Ingresar
    </Link>
  )

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Saltar al contenido
      </a>

      <header className="site-header">
        <div className="container header-inner">
          <Link className="brand" to={isAuthenticated ? homeForRole(user?.role) : '/'}>
            <span className="brand-mark" aria-hidden="true">
              T
            </span>
            <span>TicketYa</span>
          </Link>

          <nav className="nav nav-desktop" aria-label="Navegación principal">
            {links}
          </nav>

          <div className="header-actions header-actions-desktop">{actions}</div>

          <button
            className="btn btn-ghost btn-small nav-toggle"
            type="button"
            popoverTarget="nav-mobile"
            aria-controls="nav-mobile"
          >
            Menú
          </button>

          <div id="nav-mobile" popover="auto" className="nav-popover">
            <nav className="nav-mobile-links" aria-label="Navegación principal móvil">
              {links}
            </nav>
            <div className="header-actions nav-mobile-actions">{actions}</div>
          </div>
        </div>
      </header>

      {USE_MOCKS && <DemoBanner />}

      <main id="main-content" className="container main-content" tabIndex={-1}>
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
