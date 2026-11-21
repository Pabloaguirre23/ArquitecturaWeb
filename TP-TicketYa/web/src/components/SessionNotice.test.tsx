import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { SessionNotice } from './SessionNotice'

;(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true

describe('SessionNotice', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    root?.unmount()
    container?.remove()
    root = null
    container = null
  })

  it('avisa cuando el backend responde 401 en una sesión existente', async () => {
    container = document.createElement('div')
    document.body.appendChild(container)
    const createdRoot = createRoot(container)
    root = createdRoot

    await act(async () => {
      createdRoot.render(
        <MemoryRouter>
          <SessionNotice />
        </MemoryRouter>,
      )
    })

    expect(container.innerHTML).toBe('')

    await act(async () => {
      window.dispatchEvent(new Event('ticketya:unauthorized'))
    })

    expect(container.innerHTML).toContain('Tu sesión expiró')
    expect(container.querySelector('a')?.getAttribute('href')).toBe('/login')
  })
})
