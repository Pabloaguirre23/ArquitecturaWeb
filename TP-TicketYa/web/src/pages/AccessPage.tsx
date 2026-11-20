import { useState } from 'react'
import type { FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { validateAccess } from '../api/staff'
import type { AccessValidation } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { USE_MOCKS } from '../config'
import { errorMessage } from '../utils/errors'
import { formatDate } from '../utils/format'

/**
 * Pantalla de control de acceso (6.4): el personal escanea un QR y el evento.
 * El backend hace el update atómico; acá solo se muestra el resultado.
 */
export function AccessPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const [qrCode, setQrCode] = useState('')
  const [eventoId, setEventoId] = useState(params.get('eventoId') ?? '')
  const [resultado, setResultado] = useState<AccessValidation | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)

  if (user?.role !== 'ACCESS_STAFF' && user?.role !== 'ADMIN') {
    return (
      <EmptyState
        title="Solo personal de acceso"
        description="Esta pantalla es para usuarios con rol ACCESS_STAFF o ADMIN."
      />
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setResultado(null)
    setCargando(true)
    try {
      setResultado(await validateAccess(qrCode, eventoId))
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setCargando(false)
    }
  }

  return (
    <section className="page-gap">
      <div className="page-head">
        <span className="badge">Control de acceso</span>
        <h1>Validar una entrada</h1>
        <p className="muted">
          Escaneá el QR y verificá que corresponda al evento. Cada entrada se puede usar una sola vez.
        </p>
      </div>

      <form className="card form-stack" onSubmit={handleSubmit} aria-busy={cargando}>
        <div className="field">
          <label htmlFor="qr-code">Código QR</label>
          <input
            id="qr-code"
            name="qrCode"
            value={qrCode}
            onChange={(event) => setQrCode(event.target.value)}
            placeholder="235e9e9e-3451-4605-94a1-ae9cd55aef18"
            autoComplete="off"
            spellCheck={false}
            enterKeyHint="next"
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'access-error' : undefined}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="evento-id">ID del evento</label>
          <input
            id="evento-id"
            name="eventoId"
            value={eventoId}
            onChange={(event) => setEventoId(event.target.value)}
            placeholder="d0000000-0000-4000-8000-000000000001"
            autoComplete="off"
            spellCheck={false}
            enterKeyHint="go"
            required
          />
        </div>
        <button className="btn btn-primary" type="submit" disabled={cargando}>
          {cargando ? 'Validando…' : 'Validar entrada'}
        </button>
        {USE_MOCKS ? (
          <p className="hint">
            En modo demo: un QR que empieza con <code>usado-</code> simula 409, con{' '}
            <code>otro-</code> simula otro evento y con <code>no-</code> simula 404.
          </p>
        ) : (
          <p className="hint">
            Copiá el código QR y el ID del evento desde “Mis entradas” con el botón
            “Copiar QR + evento” y pegalos acá.
          </p>
        )}
      </form>

      {error && (
        <div id="access-error">
          <ErrorState message={error} />
        </div>
      )}

      {resultado && (
        <div className="card" role="status">
          <h2>Ingreso válido</h2>
          <dl className="summary-list">
            <div>
              <dt>Entrada</dt>
              <dd>{resultado.entradaId}</dd>
            </div>
            <div>
              <dt>Evento</dt>
              <dd>{resultado.eventoId}</dd>
            </div>
            <div>
              <dt>Estado</dt>
              <dd>{resultado.estado}</dd>
            </div>
            <div>
              <dt>Usada</dt>
              <dd>{formatDate(resultado.usedAt)}</dd>
            </div>
          </dl>
        </div>
      )}
    </section>
  )
}
