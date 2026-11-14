import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { Layout } from './Layout'

describe('Layout', () => {
  it('ofrece skip link, landmark principal y navegación etiquetada', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <AuthProvider>
          <Layout />
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(html).toContain('href="#main-content"')
    expect(html).toContain('id="main-content"')
    expect(html).toContain('tabindex="-1"')
    expect(html).toContain('aria-label="Navegación principal"')
    expect(html).toContain('TicketYa')
  })
})
