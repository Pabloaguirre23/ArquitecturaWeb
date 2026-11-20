import { USE_MOCKS } from '../config'
import { get, post } from './client'
import { mockAccessValidation, mockEventReport } from './mockData'
import type { AccessValidation, EventReport } from './types'

export async function validateAccess(qrCode: string, eventoId: string): Promise<AccessValidation> {
  if (USE_MOCKS) {
    return mockAccessValidation(qrCode, eventoId)
  }
  return post<AccessValidation>('/orders/access/validate', { qrCode, eventoId })
}

export async function getEventReport(eventoId: string): Promise<EventReport> {
  if (USE_MOCKS) {
    return mockEventReport(eventoId)
  }
  return get<EventReport>(`/orders/reports/events/${eventoId}`)
}
