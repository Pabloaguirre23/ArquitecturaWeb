import { USE_MOCKS } from '../config'
import { get, post } from './client'
import { mockOrder, mockReservation, mockTickets } from './mockData'
import type {
  CreateOrderRequest,
  CreateReservationItem,
  Order,
  PageResponse,
  Reservation,
  Ticket,
} from './types'

export async function createReservation(
  eventId: string,
  items: CreateReservationItem[],
): Promise<Reservation> {
  if (USE_MOCKS) {
    return mockReservation(eventId, items)
  }
  return post<Reservation>('/inventory/reservations', { eventoId: eventId, items })
}

export async function createOrder(
  request: CreateOrderRequest,
  idempotencyKey: string,
): Promise<Order> {
  if (USE_MOCKS) {
    return mockOrder(request)
  }
  return post<Order>('/orders', request, { 'Idempotency-Key': idempotencyKey })
}

export async function getTickets(): Promise<PageResponse<Ticket>> {
  if (USE_MOCKS) {
    return mockTickets()
  }
  // El backend pagina (default 20, máximo 100) y esta pantalla no pagina:
  // se pide el máximo permitido por el contrato.
  return get<PageResponse<Ticket>>('/orders/tickets?page=0&size=100')
}
