import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { homeForRole } from '../auth/roles'
import { useAuth } from '../auth/useAuth'
import { DEMO_EMAIL, DEMO_PASSWORD, USE_MOCKS } from '../config'
import { errorMessage } from '../utils/errors'

export function LoginPage() {
  const { login, isAuthenticated, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState(USE_MOCKS ? DEMO_EMAIL : '')
  const [password, setPassword] = useState(USE_MOCKS ? DEMO_PASSWORD : '')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname

  if (isAuthenticated) {
    return <Navigate to={from ?? homeForRole(user?.role)} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const logged = await login(email, password)
      navigate(from ?? homeForRole(logged.role), { replace: true })
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <span className="badge">TicketYa</span>
        <h1>Ingresá a tu cuenta</h1>
        <p>Comprá entradas, elegí tus lugares y consultá tus compras.</p>

        <form className="form-stack" onSubmit={handleSubmit} aria-busy={loading}>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              enterKeyHint="next"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="password">Contraseña</label>
            <div className="password-row">
              <input
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                enterKeyHint="go"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
              <button
                className="btn btn-ghost btn-small"
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                aria-controls="password"
                aria-pressed={showPassword}
              >
                {showPassword ? 'Ocultar' : 'Mostrar'}
              </button>
            </div>
          </div>

          {USE_MOCKS && (
            <p className="hint">
              Demo: <strong>{DEMO_EMAIL}</strong> / <strong>{DEMO_PASSWORD}</strong>
              <br />
              Organizador: <strong>organizador@ticketya.test</strong> / demo · Acceso:{' '}
              <strong>acceso@ticketya.test</strong> / demo
            </p>
          )}

          {error && (
            <p id="login-error" className="form-error" role="alert">
              {error}
            </p>
          )}

          <button className="btn btn-primary" type="submit" disabled={loading}>
            {loading ? 'Ingresando…' : 'Ingresar'}
          </button>
        </form>

        <p className="hint auth-footer">
          ¿Solo querés mirar? <Link to="/">Volver al catálogo</Link>
        </p>
      </div>
    </div>
  )
}
