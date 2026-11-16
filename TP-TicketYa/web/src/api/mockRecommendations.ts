import { readAuth } from '../auth/storage'
import { ApiError } from './client'
import { MOCK_EVENTS, mockTickets } from './mockData'
import type {
  EventDetail,
  Preferences,
  PreferencesRequest,
  RecommendationsResponse,
  RecommendationItem,
  SimilarEventsResponse,
} from './types'

const PREFERENCES_KEY = 'ticketya.mock.preferences'

const DEFAULT_PREFERENCES: Preferences = {
  tipos: ['RECITAL'],
  artistas: ['Los Piojos'],
  ciudad: 'Tandil',
  actualizadoEn: null,
}

const POPULARIDAD: Record<string, number> = {
  'd0000000-0000-4000-8000-000000000001': 3,
  'd0000000-0000-4000-8000-000000000002': 2,
  'd0000000-0000-4000-8000-000000000003': 1,
  'd0000000-0000-4000-8000-000000000004': 2,
  'd0000000-0000-4000-8000-000000000005': 4,
}

function clienteId(): string {
  return readAuth()?.user.id ?? 'demo-client'
}

function readStoredPreferences(): Preferences {
  const raw = sessionStorage.getItem(PREFERENCES_KEY)
  if (!raw) {
    return { ...DEFAULT_PREFERENCES }
  }
  try {
    return JSON.parse(raw) as Preferences
  } catch {
    sessionStorage.removeItem(PREFERENCES_KEY)
    return { ...DEFAULT_PREFERENCES }
  }
}

function normalize(values: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const value of values) {
    const trimmed = value.trim()
    if (!trimmed) {
      continue
    }
    const key = trimmed.toLowerCase()
    if (!seen.has(key)) {
      seen.add(key)
      result.push(trimmed)
    }
  }
  return result
}

function comprados(): Set<string> {
  return new Set(mockTickets().content.map((ticket) => ticket.eventoId))
}

function puntuar(event: EventDetail, preferences: Preferences): { puntaje: number; motivos: string[] } {
  let puntaje = 0
  const motivos: string[] = []

  if (preferences.tipos.some((tipo) => tipo.toLowerCase() === event.tipo.toLowerCase())) {
    puntaje += 3
    motivos.push(`Coincide con tu tipo favorito: ${event.tipo}`)
  }

  const artista = event.participantes.find((participante) =>
    preferences.artistas.some((favorito) => favorito.toLowerCase() === participante.toLowerCase()),
  )
  if (artista) {
    puntaje += 4
    motivos.push(`Artista favorito: ${artista}`)
  }

  if (preferences.ciudad && event.ciudad.toLowerCase() === preferences.ciudad.toLowerCase()) {
    puntaje += 2
    motivos.push(`En tu ciudad: ${event.ciudad}`)
  }

  const compras = POPULARIDAD[event.id] ?? 0
  if (compras > 0) {
    puntaje += Math.min(compras, 5)
    motivos.push(`Popular: ${compras} compra(s) registrada(s)`)
  }

  if (motivos.length === 0) {
    motivos.push('Evento publicado y próximo')
  }

  return { puntaje, motivos }
}

export function mockGetPreferences(): Preferences {
  return { ...readStoredPreferences(), clienteId: clienteId() }
}

export function mockSavePreferences(request: PreferencesRequest): Preferences {
  if (!request.ciudad || !request.ciudad.trim()) {
    throw new ApiError(400, 'La ciudad es obligatoria.', 'VALIDATION_ERROR')
  }

  const preferences: Preferences = {
    clienteId: clienteId(),
    tipos: normalize(request.tipos),
    artistas: normalize(request.artistas),
    ciudad: request.ciudad.trim(),
    actualizadoEn: new Date().toISOString(),
  }
  sessionStorage.setItem(PREFERENCES_KEY, JSON.stringify(preferences))
  return preferences
}

export function mockRecommendations(): RecommendationsResponse {
  const preferences = readStoredPreferences()
  const compradosPorCliente = comprados()

  const candidatos = MOCK_EVENTS.filter((event) => !compradosPorCliente.has(event.id))
    .map((event) => {
      const { puntaje, motivos } = puntuar(event, preferences)
      return {
        eventoId: event.id,
        nombre: event.nombre,
        tipo: event.tipo,
        artistas: event.participantes,
        ciudad: event.ciudad,
        fechaEvento: event.fecha,
        altaDemanda: Boolean(event.altaDemanda),
        puntaje,
        motivos,
      } satisfies RecommendationItem
    })
    .sort(
      (a, b) =>
        b.puntaje - a.puntaje ||
        new Date(a.fechaEvento).getTime() - new Date(b.fechaEvento).getTime() ||
        a.nombre.localeCompare(b.nombre),
    )

  const conAfinidad = candidatos.filter((item) => item.puntaje > 0)
  const recomendaciones = (conAfinidad.length > 0 ? conAfinidad : candidatos).slice(0, 6)

  return {
    clienteId: clienteId(),
    generadoEn: new Date().toISOString(),
    recomendaciones,
  }
}

export function mockSimilarEvents(eventoId: string): SimilarEventsResponse {
  const objetivo = MOCK_EVENTS.find((event) => event.id === eventoId)
  if (!objetivo) {
    throw new ApiError(404, 'El evento no existe o no está publicado.', 'EVENT_NOT_FOUND')
  }

  const compradosPorCliente = comprados()
  const similares = MOCK_EVENTS.filter(
    (event) => event.id !== eventoId && !compradosPorCliente.has(event.id),
  )
    .map((event) => {
      let afinidad = 0
      if (event.tipo === objetivo.tipo) {
        afinidad += 2
      }
      if (event.ciudad === objetivo.ciudad) {
        afinidad += 1
      }
      afinidad +=
        event.participantes.filter((participante) => objetivo.participantes.includes(participante))
          .length * 3
      return { event, afinidad }
    })
    .filter(({ afinidad }) => afinidad > 0)
    .sort(
      (a, b) =>
        b.afinidad - a.afinidad ||
        new Date(a.event.fecha).getTime() - new Date(b.event.fecha).getTime(),
    )
    .slice(0, 4)
    .map(({ event, afinidad }) => ({
      eventoId: event.id,
      nombre: event.nombre,
      tipo: event.tipo,
      artistas: event.participantes,
      ciudad: event.ciudad,
      fechaEvento: event.fecha,
      compradoresEnComun: Math.max(1, afinidad),
    }))

  return {
    eventoId,
    generadoEn: new Date().toISOString(),
    similares,
  }
}
