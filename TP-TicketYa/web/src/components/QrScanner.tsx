import { useEffect, useRef, useState } from 'react'

interface DetectedBarcode {
  rawValue: string
}

interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<DetectedBarcode[]>
}

type BarcodeDetectorConstructor = new (options?: { formats?: string[] }) => BarcodeDetectorLike

interface BarcodeDetectorWindow extends Window {
  BarcodeDetector?: BarcodeDetectorConstructor
}

/**
 * Escaneo progresivo del QR con la BarcodeDetector API. En navegadores sin
 * soporte (o sin cámara) el componente no se muestra y el control de acceso
 * sigue funcionando con el código pegado a mano.
 */
export function QrScanner({ onDetected }: { onDetected: (value: string) => void }) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const timerRef = useRef<number | null>(null)
  const busyRef = useRef(false)
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [supported] = useState(() => {
    if (typeof window === 'undefined') {
      return false
    }
    const detector = (window as BarcodeDetectorWindow).BarcodeDetector
    return Boolean(detector) && Boolean(navigator.mediaDevices?.getUserMedia)
  })

  function stop() {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    if (videoRef.current) {
      videoRef.current.srcObject = null
    }
    setScanning(false)
  }

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) {
        window.clearInterval(timerRef.current)
      }
      streamRef.current?.getTracks().forEach((track) => track.stop())
    }
  }, [])

  if (!supported) {
    return null
  }

  async function start() {
    setError(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
      })
      streamRef.current = stream
      if (!videoRef.current) {
        stop()
        return
      }
      videoRef.current.srcObject = stream
      await videoRef.current.play()

      const Detector = (window as BarcodeDetectorWindow).BarcodeDetector
      if (!Detector) {
        stop()
        return
      }
      const detector = new Detector({ formats: ['qr_code'] })
      setScanning(true)
      timerRef.current = window.setInterval(async () => {
        if (busyRef.current || !videoRef.current) {
          return
        }
        busyRef.current = true
        try {
          const codes = await detector.detect(videoRef.current)
          const value = codes[0]?.rawValue?.trim()
          if (value) {
            onDetected(value)
            stop()
          }
        } catch {
          // Un frame ilegible no corta el escaneo; se reintenta en el próximo.
        } finally {
          busyRef.current = false
        }
      }, 300)
    } catch {
      stop()
      setError('No se pudo abrir la cámara. Revisá los permisos o pegá el código a mano.')
    }
  }

  return (
    <div className="scanner">
      <video
        ref={videoRef}
        className={scanning ? 'scanner-video' : 'scanner-video scanner-hidden'}
        muted
        playsInline
        aria-label="Cámara para escanear el QR"
      />
      {scanning ? (
        <button className="btn btn-ghost btn-small" type="button" onClick={stop}>
          Detener escaneo
        </button>
      ) : (
        <button className="btn btn-ghost btn-small" type="button" onClick={start}>
          Escanear QR con la cámara
        </button>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
