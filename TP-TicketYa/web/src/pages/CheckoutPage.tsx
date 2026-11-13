import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { createOrder } from '../api/orders'
import type { MedioPago, Order, Reservation } from '../api/types'
import { EmptyState } from '../components/EmptyState'
import { Loading } from '../components/Loading'
import { QrPlaceholder } from '../components/QrPlaceholder'
import { USE_MOCKS } from '../config'
import { errorMessage } from '../utils/errors'
import { formatCurrency, formatDate } from '../utils/format'
import { newIdempotencyKey } from '../utils/idempotency'
import { clearReservation, readReservation } from '../utils/reservationStorage'

const paymentOptions: Array<{ value: MedioPago; label: string }> = [
  { value: 'TARJETA', label: 'Tarjeta de crédito' },
  { value: 'TRANSFERENCIA', label: 'Transferencia' },
  { value: 'EFECTIVO', label: 'Efectivo' },
]

export function CheckoutPage() {
  const [reservation] = useState<Reservation | null>(() => readReservation())
  const [medioPago, setMedioPago] = useState<MedioPago>('TARJETA')
  const [paymentToken, setPaymentToken] = useState('APROBADO')
  const [idempotencyKey] = useState(() => newIdempotencyKey())
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!reservation) {
      return
    }
    setLoading(true)
    setError(null)
    try {
      const result = await createOrder(
        { reservaId: reservation.id, medioPago, token: paymentToken },
        idempotencyKey,
      )
      setOrder(result)
      clearReservation()
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setLoading(false)
    }
  }

  if (!reservation && !order) {
    return (
      <div className="section-gap">
        <EmptyState
          title="No hay una reserva activa"
          description="Elegí un evento y tus lugares para iniciar una compra."
          actionLabel="Buscar eventos"
        />
      </div>
    )
  }

  if (order?.estado === 'PAGADA') {
    return (
      <div className="page-gap">
        <div className="success-box" role="status">
          <strong>¡Compra confirmada!</strong>
          <p>Tu pago fue aprobado y las entradas ya están disponibles.</p>
        </div>

        <div className="card">
          <h2>Entradas emitidas</h2>
          <div className="ticket-list">
            {order.entradas.map((ticket) => (
              <div className="ticket-card" key={ticket.id}>
                <QrPlaceholder value={ticket.qrCode} />
                <div>
                  <span className="badge ok">{ticket.estado}</span>
                  <h3>{ticket.eventoNombre}</h3>
                  <p className="muted">{formatDate(ticket.eventoFecha)}</p>
                  <p className="mono">{ticket.qrCode}</p>
                </div>
              </div>
            ))}
          </div>
          <div className="actions-row">
            <Link className="btn btn-primary" to="/tickets">
              Ver mis entradas
            </Link>
            <Link className="btn btn-ghost" to="/">
              Seguir buscando
            </Link>
          </div>
        </div>
      </div>
    )
  }

  if (order) {
    return (
      <div className="page-gap">
        <div className="error-box" role="alert">
          <strong>El pago no se pudo completar.</strong>
          <p>La orden quedó en estado {order.estado}. No se emitieron entradas.</p>
        </div>
        <EmptyState
          title="Probá con otro medio de pago"
          description="La reserva se liberó. Podés volver al evento y elegir tus lugares de nuevo."
          actionLabel="Volver al evento"
          actionTo={`/events/${order.eventoId}`}
        />
      </div>
    )
  }

  if (!reservation) {
    return <Loading />
  }

  return (
    <div className="page-gap">
      <Link className="back-link" to={`/events/${reservation.eventoId}`}>
        ← Volver al evento
      </Link>

      <header className="page-head">
        <span className="badge">Checkout</span>
        <h1>Confirmá tu compra</h1>
        <p className="muted">
          Reserva válida hasta {formatDate(reservation.expiresAt)}. Después de ese plazo los
          lugares se liberan.
        </p>
      </header>

      <div className="detail-grid">
        <section className="card">
          <h2>{reservation.eventoNombre}</h2>
          <p className="muted">{formatDate(reservation.eventoFecha)}</p>

          <div className="summary-list">
            {reservation.items.map((item, index) => (
              <div className="summary-row" key={`${item.sectorId}-${item.asientoId ?? index}`}>
                <span>
                  Sector {item.sectorId.slice(0, 8)} · {item.cantidad} lugar(es)
                </span>
                <strong>{formatCurrency(item.subtotal)}</strong>
              </div>
            ))}
          </div>

          <div className="summary-row summary-total">
            <span>Total</span>
            <span>{formatCurrency(reservation.total)}</span>
          </div>

          <form className="form-stack checkout-form" onSubmit={handleSubmit}>
            <div className="field">
              <label htmlFor="medioPago">Medio de pago</label>
              <select
                id="medioPago"
                value={medioPago}
                onChange={(event) => setMedioPago(event.target.value as MedioPago)}
              >
                {paymentOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            {USE_MOCKS && (
              <div className="field">
                <label htmlFor="paymentToken">Resultado simulado de la pasarela</label>
                <select
                  id="paymentToken"
                  value={paymentToken}
                  onChange={(event) => setPaymentToken(event.target.value)}
                >
                  <option value="APROBADO">Aprobado</option>
                  <option value="RECHAZADO">Rechazado</option>
                  <option value="LENTO">Lento (timeout)</option>
                </select>
              </div>
            )}

            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}

            <button className="btn btn-primary" type="submit" disabled={loading}>
              {loading ? 'Procesando pago…' : `Pagar ${formatCurrency(reservation.total)}`}
            </button>

            <p className="hint">
              Clave de idempotencia: <code>{idempotencyKey.slice(0, 8)}…</code>. Reintentar no
              duplica la compra.
            </p>
          </form>
        </section>

        <aside className="card summary">
          <h2>Detalle</h2>
          <div className="summary-row">
            <span>Reserva</span>
            <span className="mono">{reservation.id.slice(0, 8)}…</span>
          </div>
          <div className="summary-row">
            <span>Estado</span>
            <span className="badge ok">{reservation.estado}</span>
          </div>
          <div className="summary-row">
            <span>Lugares</span>
            <strong>{reservation.items.reduce((sum, item) => sum + item.cantidad, 0)}</strong>
          </div>
          <p className="hint">
            Al confirmar, se emite una entrada con QR único por cada lugar.
          </p>
        </aside>
      </div>
    </div>
  )
}
