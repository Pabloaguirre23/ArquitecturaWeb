import { useEffect, useState } from 'react'
import QRCode from 'qrcode'

/**
 * QR real de la entrada (punto 8 del enunciado). El backend entrega el código
 * como UUID; acá se convierte en una imagen escaneable por cualquier lector,
 * incluido el control de acceso. Si el navegador no puede generarlo, se muestra
 * el código en texto para que el personal lo pueda tipear.
 */
export function QrCode({ value, size = 120 }: { value: string; size?: number }) {
  const [dataUrl, setDataUrl] = useState<string | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    setDataUrl(null)
    setError(false)

    QRCode.toDataURL(value, {
      width: size,
      margin: 1,
      errorCorrectionLevel: 'M',
      color: { dark: '#161826', light: '#ffffff' },
    })
      .then((url) => {
        if (!cancelled) {
          setDataUrl(url)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError(true)
        }
      })

    return () => {
      cancelled = true
    }
  }, [value, size])

  if (error) {
    return (
      <div className="qr qr-fallback" role="img" aria-label={`Código QR de la entrada ${value}`}>
        <span>{value.slice(0, 8)}…</span>
      </div>
    )
  }

  if (!dataUrl) {
    return <div className="qr qr-loading" aria-hidden="true" />
  }

  return (
    <img
      className="qr"
      src={dataUrl}
      width={size}
      height={size}
      alt={`Código QR de la entrada ${value}`}
    />
  )
}
