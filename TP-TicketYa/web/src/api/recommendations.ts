import { USE_MOCKS } from '../config'
import { get, put } from './client'
import {
  mockGetPreferences,
  mockRecommendations,
  mockSavePreferences,
  mockSimilarEvents,
} from './mockRecommendations'
import type {
  Preferences,
  PreferencesRequest,
  RecommendationsResponse,
  SimilarEventsResponse,
} from './types'

export async function getRecommendations(): Promise<RecommendationsResponse> {
  if (USE_MOCKS) {
    return mockRecommendations()
  }
  return get<RecommendationsResponse>('/recommendations')
}

export async function getPreferences(): Promise<Preferences> {
  if (USE_MOCKS) {
    return mockGetPreferences()
  }
  return get<Preferences>('/recommendations/preferences')
}

export async function savePreferences(request: PreferencesRequest): Promise<Preferences> {
  if (USE_MOCKS) {
    return mockSavePreferences(request)
  }
  return put<Preferences>('/recommendations/preferences', request)
}

export async function getSimilarEvents(eventoId: string): Promise<SimilarEventsResponse> {
  if (USE_MOCKS) {
    return mockSimilarEvents(eventoId)
  }
  return get<SimilarEventsResponse>(`/recommendations/events/${eventoId}/similar`)
}
