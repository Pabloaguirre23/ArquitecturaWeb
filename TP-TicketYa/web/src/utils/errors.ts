import { ApiError } from '../api/client'

/**
 * Mensajes pensados para el usuario final. Los códigos que la UI no puede
 * resolver sola (fila virtual, reserva vencida) se traducen a una explicación
 * accionable en lugar de mostrar el `detail` técnico del backend.
 */
const MENSAJES: Record<string, string> = {
  QUEUE_REQUIRED:
    'Este evento tiene fila virtual. Unite a la fila desde la API (o esperá tu turno en la app) y volvé a intentar con el token de la fila.',
  QUEUE_TOKEN_INVALID:
    'El token de la fila venció o no corresponde a este evento. Volvé a ingresar a la fila para comprar.',
  QUEUE_NOT_OPEN:
    'La fila virtual de este evento todavía no está abierta. Probá de nuevo cuando comience la venta.',
  RESERVATION_EXPIRED:
    'La reserva venció y los lugares se liberaron. Elegí tus lugares de nuevo.',
  RESERVATION_ALREADY_PAID:
    'Esa reserva ya se usó en otra compra. Revisá tus entradas antes de volver a pagar.',
  IDEMPOTENCY_KEY_REUSED:
    'La clave de idempotencia ya corresponde a otra operación. La regeneramos para reintentar.',
}

export function errorCode(error: unknown): string | null {
  return error instanceof ApiError ? (error.code ?? null) : null
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const traducido = error.code ? MENSAJES[error.code] : undefined
    return traducido ?? error.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return 'Ocurrió un error inesperado.'
}
