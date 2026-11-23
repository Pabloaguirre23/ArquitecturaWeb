import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { createOrder } from '../api/orders'
import type { MedioPago, Order, Reservation } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { EmptyState } from '../components/EmptyState'
import { Loading } from '../components/Loading'
import { QrCode } from '../components/QrCode'
import { errorCode, errorMessage } from '../utils/errors'
import { formatCountdown, formatCurrency, formatDate } from '../utils/format'
import { newIdempotencyKey } from '../utils/idempotency'
import { clearReservation, readReservation } from '../utils/reservationStorage'

const paymentOptions: Array<{ value: MedioPago; label: string }> = [
  { value: 'TARJETA', label: 'Tarjeta de crédito' },
  { value: 'TRANSFERENCIA', label: 'Transferencia' },
  { value: 'EFECTIVO', label: 'Efectivo' },
]

export function CheckoutPage() {
  const { user } = useAuth()
  const [reservation] = useState<Reservation | null>(() => readReservation())
  const [medioPago, setMedioPago] = useState<MedioPago>('TARJETA')
  const [paymentToken, setPaymentToken] = useState('APROBADO')
  const [idempotencyKey, setIdempotencyKey] = useState(() => newIdempotencyKey())
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errorActual, setErrorActual] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())

  const reservaVencida = reservation
    ? new Date(reservation.expiresAt).getTime() <= now
    : false

  useEffect(() => {
    if (!reservation || order) {
      return
    }
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [reservation, order])

  // La clave de idempotencia identifica el cuerpo de la compra. Si cambia el
  // medio de pago o el resultado simulado, se regenera para no reutilizar una
  // clave con otro cuerpo (422 IDEMPOTENCY_KEY_REUSED).
  function cambiarMedioPago(valor: MedioPago) {
    setMedioPago(valor)
    setIdempotencyKey(newIdempotencyKey())
  }

  function cambiarTokenPago(valor: string) {
    setPaymentToken(valor)
    setIdempotencyKey(newIdempotencyKey())
  }

  if (user && user.role !== 'CLIENT') {
    return (
      <EmptyState
        title="Solo para clientes"
        description="La compra de entradas es un servicio para cuentas CLIENT. Esta sesión no puede iniciar un checkout."
      />
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!reservation || reservaVencida) {
      return
    }
    setLoading(true)
    setError(null)
    setErrorActual(null)
    try {
      const result = await createOrder(
        { reservaId: reservation.id, medioPago, token: paymentToken },
        idempotencyKey,
      )
      setOrder(result)
      clearReservation()
    } catch (caught) {
      const code = errorCode(caught)
      setErrorActual(code)
      setError(errorMessage(caught))
      if (code === 'IDEMPOTENCY_KEY_REUSED') {
        setIdempotencyKey(newIdempotencyKey())
      }
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
                <QrCode value={ticket.qrCode} />
                <div>
                  <span className="badge ok">{ticket.estado}</span>
                  <h3>{order.eventoNombre}</h3>
                  <p className="muted">{formatDate(order.eventoFecha)}</p>
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
    const reintegroEnCurso = order.estado === 'REINTEGRO_PENDIENTE'
    return (
      <div className="page-gap">
        <div className="error-box" role="alert">
          {reintegroEnCurso ? (
            <>
              <strong>El pago se cobró, pero la compra no se completó.</strong>
              <p>
                La orden quedó en estado {order.estado}: el reintegro está en curso y no se
                emitieron entradas.
              </p>
            </>
          ) : (
            <>
              <strong>El pago no se pudo completar.</strong>
              <p>La orden quedó en estado {order.estado}. No se emitieron entradas.</p>
            </>
          )}
        </div>
        <EmptyState
          title="Volvé a elegir tus lugares"
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
          Reserva válida hasta {formatDate(reservation.expiresAt)}.{' '}
          {reservaVencida ? (
            <strong className="countdown countdown-expired">Reserva vencida</strong>
          ) : (
            <>
              Quedan <strong className="countdown">{formatCountdown(reservation.expiresAt)}</strong>.
              Después de ese plazo los lugares se liberan.
            </>
          )}
        </p>
      </header>

      {reservaVencida && (
        <div className="error-box" role="alert">
          <strong>La reserva venció.</strong>
          <p>Los lugares se liberaron y no se puede completar el pago con esta reserva.</p>
          <Link className="btn btn-primary" to={`/events/${reservation.eventoId}`}>
            Elegir lugares de nuevo
          </Link>
        </div>
      )}

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

          <form className="form-stack checkout-form" onSubmit={handleSubmit} aria-busy={loading}>
            <div className="field">
              <label htmlFor="medioPago">Medio de pago</label>
              <select
                id="medioPago"
                name="medioPago"
                value={medioPago}
                onChange={(event) => cambiarMedioPago(event.target.value as MedioPago)}
              >
                {paymentOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="paymentToken">Resultado de la pasarela (demo)</label>
              <select
                id="paymentToken"
                name="paymentToken"
                value={paymentToken}
                onChange={(event) => cambiarTokenPago(event.target.value)}
              >
                <option value="APROBADO">Aprobado</option>
                <option value="RECHAZADO">Rechazado</option>
                <option value="LENTO">Lento (timeout)</option>
              </select>
              <p className="hint">
                La pasarela de la Entrega 2 es mock: el token elige el resultado y permite
                demostrar rechazo y timeout también contra el backend real.
              </p>
            </div>

            {error && (
              <div className="form-error" role="alert">
                <p id="checkout-error">{error}</p>
                {(errorActual === 'RESERVATION_EXPIRED' || reservaVencida) && (
                  <Link className="btn btn-ghost" to={`/events/${reservation.eventoId}`}>
                    Elegir lugares de nuevo
                  </Link>
                )}
              </div>
            )}

            <button
              className="btn btn-primary"
              type="submit"
              disabled={loading || reservaVencida}
              aria-describedby={error ? 'checkout-error' : undefined}
            >
              {loading
                ? 'Procesando pago…'
                : reservaVencida
                  ? 'Reserva vencida'
                  : `Pagar ${formatCurrency(reservation.total)}`}
            </button>

            <p className="hint">
              Clave de idempotencia: <code>{idempotencyKey.slice(0, 8)}…</code>. Reintentar no
              duplica la compra.
            </p>
          </form>
        </section>

        <aside className="card summary" aria-label="Resumen de la compra">
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
