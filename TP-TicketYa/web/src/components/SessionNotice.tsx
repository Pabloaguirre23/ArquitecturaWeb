import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

/**
 * El cliente HTTP emite `ticketya:unauthorized` cuando un endpoint protegido
 * responde 401. AuthProvider cierra la sesión; este aviso explica por qué, para
 * que la demo no parezca un logout espontáneo.
 */
export function SessionNotice() {
  const [visible, setVisible] = useState(false)
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    const handleUnauthorized = () => {
      setVisible(true)
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
      }
      timerRef.current = window.setTimeout(() => setVisible(false), 8000)
    }
    window.addEventListener('ticketya:unauthorized', handleUnauthorized)
    return () => {
      window.removeEventListener('ticketya:unauthorized', handleUnauthorized)
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
      }
    }
  }, [])

  if (!visible) {
    return null
  }

  return (
    <div className="session-toast" role="status">
      <span>Tu sesión expiró.</span>
      <Link to="/login">Ingresá de nuevo</Link>
      <button
        className="session-toast-close"
        type="button"
        aria-label="Cerrar aviso"
        onClick={() => setVisible(false)}
      >
        ×
      </button>
    </div>
  )
}
