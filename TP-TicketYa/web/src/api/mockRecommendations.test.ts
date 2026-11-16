import { beforeEach, describe, expect, it } from 'vitest'
import {
  mockGetPreferences,
  mockRecommendations,
  mockSavePreferences,
  mockSimilarEvents,
} from './mockRecommendations'

const FESTIVAL = 'd0000000-0000-4000-8000-000000000001'
const TEATRO = 'd0000000-0000-4000-8000-000000000002'
const CHARLA = 'd0000000-0000-4000-8000-000000000003'
const DEPORTE = 'd0000000-0000-4000-8000-000000000004'
const RECITAL = 'd0000000-0000-4000-8000-000000000005'

beforeEach(() => {
  sessionStorage.clear()
})

describe('mock de recomendaciones', () => {
  it('arranca con preferencias demo y las guarda normalizadas', () => {
    expect(mockGetPreferences().tipos).toContain('RECITAL')
    expect(mockGetPreferences().ciudad).toBe('Tandil')

    const saved = mockSavePreferences({
      tipos: [' DEPORTE ', 'deporte', ''],
      artistas: [' UNICEN ', 'unicen'],
      ciudad: '  Tandil  ',
    })

    expect(saved.tipos).toEqual(['DEPORTE'])
    expect(saved.artistas).toEqual(['UNICEN'])
    expect(saved.ciudad).toBe('Tandil')
    expect(saved.actualizadoEn).toBeTruthy()
    expect(mockGetPreferences().tipos).toEqual(['DEPORTE'])
  })

  it('rechaza una ciudad vacía', () => {
    expect(() =>
      mockSavePreferences({ tipos: ['RECITAL'], artistas: [], ciudad: '   ' }),
    ).toThrowError(/ciudad/i)
  })

  it('ordena por afinidad y excluye los eventos ya comprados', () => {
    const response = mockRecommendations()
    const ids = response.recomendaciones.map((item) => item.eventoId)

    expect(ids).not.toContain(TEATRO)
    expect(ids[0]).toBe(RECITAL)
    expect(ids[1]).toBe(FESTIVAL)
    expect(response.recomendaciones[0].motivos).toEqual(
      expect.arrayContaining([
        'Coincide con tu tipo favorito: RECITAL',
        'Artista favorito: Los Piojos',
        'En tu ciudad: Tandil',
      ]),
    )
  })

  it('prioriza el tipo que el cliente acaba de guardar', () => {
    mockSavePreferences({ tipos: ['DEPORTE'], artistas: [], ciudad: 'Tandil' })

    const response = mockRecommendations()

    expect(response.recomendaciones[0].eventoId).toBe(DEPORTE)
    expect(response.recomendaciones[0].motivos).toContain(
      'Coincide con tu tipo favorito: DEPORTE',
    )
  })

  it('calcula similares por artistas, tipo y ciudad sin repetir el evento', () => {
    const response = mockSimilarEvents(FESTIVAL)
    const ids = response.similares.map((item) => item.eventoId)

    expect(ids[0]).toBe(RECITAL)
    expect(ids).not.toContain(FESTIVAL)
    expect(ids).not.toContain(TEATRO)
    expect(response.similares[0].compradoresEnComun).toBeGreaterThan(1)
    expect(ids).toEqual(expect.arrayContaining([CHARLA, DEPORTE]))
  })

  it('responde 404 para un evento inexistente', () => {
    expect(() => mockSimilarEvents('no-existe')).toThrowError(/no existe/i)
  })
})
