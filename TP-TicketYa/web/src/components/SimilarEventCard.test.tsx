import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { SimilarEventItem } from '../api/types'
import { SimilarEventCard } from './SimilarEventCard'

const item: SimilarEventItem = {
  eventoId: 'd0000000-0000-4000-8000-000000000005',
  nombre: 'Noche de Rock en el Anfiteatro',
  tipo: 'RECITAL',
  artistas: ['Los Piojos'],
  ciudad: 'Tandil',
  fechaEvento: '2027-07-03T20:00:00-03:00',
  compradoresEnComun: 3,
}

describe('SimilarEventCard', () => {
  it('explica por qué se recomienda y enlaza al evento', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <SimilarEventCard item={item} />
      </MemoryRouter>,
    )

    expect(html).toContain('Noche de Rock en el Anfiteatro')
    expect(html).toContain('3 personas que compraron este evento también compraron este')
    expect(html).toContain('href="/events/d0000000-0000-4000-8000-000000000005"')
  })
})
