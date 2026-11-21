import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QrCode } from './QrCode'

const { toDataURL } = vi.hoisted(() => ({ toDataURL: vi.fn() }))

vi.mock('qrcode', () => ({ default: { toDataURL } }))

;(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true

describe('QrCode', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    root?.unmount()
    container?.remove()
    root = null
    container = null
    vi.clearAllMocks()
  })

  async function render(value: string) {
    container = document.createElement('div')
    document.body.appendChild(container)
    const createdRoot = createRoot(container)
    root = createdRoot
    await act(async () => {
      createdRoot.render(<QrCode value={value} />)
    })
    await act(async () => {})
  }

  it('renderiza una imagen QR real con el código de la entrada', async () => {
    toDataURL.mockResolvedValue('data:image/png;base64,qr-real')
    await render('44444444-4444-4444-8444-444444444444')

    const image = container!.querySelector('img')
    expect(image).not.toBeNull()
    expect(image!.getAttribute('src')).toBe('data:image/png;base64,qr-real')
    expect(image!.getAttribute('alt')).toContain('44444444-4444-4444-8444-444444444444')
    expect(toDataURL).toHaveBeenCalledWith(
      '44444444-4444-4444-8444-444444444444',
      expect.objectContaining({ errorCorrectionLevel: 'M' }),
    )
  })

  it('si el navegador no puede generar el QR muestra el código abreviado', async () => {
    toDataURL.mockRejectedValue(new Error('sin canvas'))
    await render('55555555-5555-4555-8555-555555555555')

    expect(container!.querySelector('img')).toBeNull()
    expect(container!.querySelector('.qr-fallback')?.textContent).toContain('55555555')
  })
})
