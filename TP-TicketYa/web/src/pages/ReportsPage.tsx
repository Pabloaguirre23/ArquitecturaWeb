import { useState } from 'react'
import type { FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getEventReport } from '../api/staff'
import type { EventReport } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { errorMessage } from '../utils/errors'
import { formatCurrency, formatDate } from '../utils/format'

/**
 * Reporte por evento (6.5) para el organizador dueño o un admin.
 * Las métricas las calcula orders; acá se muestran por sector y totales.
 */
export function ReportsPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const [eventoId, setEventoId] = useState(params.get('eventoId') ?? '')
  const [reporte, setReporte] = useState<EventReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)

  if (user?.role !== 'ORGANIZER' && user?.role !== 'ADMIN') {
    return (
      <EmptyState
        title="Solo organizadores"
        description="El reporte está disponible para el organizador dueño del evento o un ADMIN."
      />
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setReporte(null)
    setCargando(true)
    try {
      setReporte(await getEventReport(eventoId))
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setCargando(false)
    }
  }

  return (
    <section className="page-gap">
      <div className="page-head">
        <span className="badge">Organizador</span>
        <h1>Reporte del evento</h1>
        <p className="muted">
          Entradas vendidas y devueltas, ocupación y recaudación por sector.
        </p>
      </div>

      <form className="card form-stack" onSubmit={handleSubmit} aria-busy={cargando}>
        <div className="field">
          <label htmlFor="report-evento">ID del evento</label>
          <input
            id="report-evento"
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
          {cargando ? 'Consultando…' : 'Consultar reporte'}
        </button>
      </form>

      {error && <ErrorState message={error} />}

      {reporte && (
        <div className="page-gap">
          {reporte.datosDesactualizados && (
            <p className="error-box" role="status">
              Catalog no respondió: la capacidad o los precios pueden estar desactualizados.
            </p>
          )}

          <div
            className="card report-table"
            role="region"
            aria-labelledby="report-caption"
            tabIndex={0}
          >
            <table>
              <caption id="report-caption" className="sr-only">
                Reporte por sector del evento {reporte.eventoId}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Sector</th>
                  <th scope="col">Capacidad</th>
                  <th scope="col">Vendidas</th>
                  <th scope="col">Devueltas</th>
                  <th scope="col">Netas</th>
                  <th scope="col">Ocupación</th>
                  <th scope="col">Recaudación</th>
                </tr>
              </thead>
              <tbody>
                {reporte.sectores.map((sector) => (
                  <tr key={sector.sectorId}>
                    <th scope="row">{sector.sectorNombre}</th>
                    <td data-label="Capacidad">{sector.capacidad ?? '—'}</td>
                    <td data-label="Vendidas">{sector.vendidas}</td>
                    <td data-label="Devueltas">{sector.devueltas}</td>
                    <td data-label="Netas">{sector.netas}</td>
                    <td data-label="Ocupación">
                      {sector.ocupacion === null
                        ? 'No disponible'
                        : `${Math.round(sector.ocupacion * 100)} %`}
                    </td>
                    <td data-label="Recaudación">{formatCurrency(sector.recaudacion)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row">Total</th>
                  <td data-label="Capacidad">—</td>
                  <td data-label="Vendidas">{reporte.totales.vendidas}</td>
                  <td data-label="Devueltas">{reporte.totales.devueltas}</td>
                  <td data-label="Netas">{reporte.totales.netas}</td>
                  <td data-label="Ocupación">—</td>
                  <td data-label="Recaudación">{formatCurrency(reporte.totales.recaudacion)}</td>
                </tr>
              </tfoot>
            </table>
          </div>

          <p className="muted">Generado: {formatDate(reporte.generadoEn)}</p>
        </div>
      )}
    </section>
  )
}
