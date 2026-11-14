import { USE_MOCKS } from '../config'
import { get } from './client'
import { mockEvent, mockSearch, mockSeats } from './mockData'
import type { EventDetail, EventSummary, PageResponse, SearchParams, Seat } from './types'

export async function searchEvents(params: SearchParams): Promise<PageResponse<EventSummary>> {
  if (USE_MOCKS) {
    return mockSearch(params)
  }

  const query = new URLSearchParams()
  if (params.q) {
    query.set('participante', params.q)
  }
  if (params.tipo) {
    query.set('tipo', params.tipo)
  }
  if (params.ciudad) {
    query.set('ciudad', params.ciudad)
  }
  if (params.fechaDesde) {
    query.set('fechaDesde', `${params.fechaDesde}T00:00:00-03:00`)
  }
  if (params.fechaHasta) {
    query.set('fechaHasta', `${params.fechaHasta}T23:59:59-03:00`)
  }
  query.set('orden', params.sort ?? 'FECHA_ASC')
  query.set('page', String(params.page ?? 0))
  query.set('size', String(params.size ?? 6))

  return get<PageResponse<EventSummary>>(`/catalog/events?${query.toString()}`)
}

export async function getEvent(eventId: string): Promise<EventDetail> {
  if (USE_MOCKS) {
    return mockEvent(eventId)
  }
  return get<EventDetail>(`/catalog/events/${eventId}`)
}

export async function getSeats(eventId: string, sectorId: string): Promise<Seat[]> {
  if (USE_MOCKS) {
    return mockSeats(eventId, sectorId)
  }
  return get<Seat[]>(`/inventory/events/${eventId}/seats?sectorId=${sectorId}`)
}
