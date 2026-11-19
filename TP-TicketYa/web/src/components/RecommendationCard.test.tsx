import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { RecommendationItem } from '../api/types'
import { RecommendationCard } from './RecommendationCard'

const item: RecommendationItem = {
  eventoId: 'd0000000-0000-4000-8000-000000000005',
  nombre: 'Noche de Rock en el Anfiteatro',
  tipo: 'RECITAL',
  artistas: ['Los Piojos'],
  ciudad: 'Tandil',
  fechaEvento: '2027-07-03T20:00:00-03:00',
  altaDemanda: true,
  puntaje: 9,
  motivos: ['Artista favorito: Los Piojos', 'En tu ciudad: Tandil'],
}

describe('RecommendationCard', () => {
  it('muestra el evento, sus motivos y la afinidad', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <RecommendationCard item={item} />
      </MemoryRouter>,
    )

    expect(html).toContain('Noche de Rock en el Anfiteatro')
    expect(html).toContain('Artista favorito: Los Piojos')
    expect(html).toContain('En tu ciudad: Tandil')
    expect(html).toContain('Afinidad 9')
    expect(html).toContain('href="/events/d0000000-0000-4000-8000-000000000005"')
  })
})
