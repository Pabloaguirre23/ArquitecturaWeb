import type { Reservation } from '../api/types'

const RESERVATION_KEY = 'ticketya.reservation'

export function saveReservation(reservation: Reservation): void {
  sessionStorage.setItem(RESERVATION_KEY, JSON.stringify(reservation))
}

export function readReservation(): Reservation | null {
  const raw = sessionStorage.getItem(RESERVATION_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as Reservation
  } catch {
    sessionStorage.removeItem(RESERVATION_KEY)
    return null
  }
}

export function clearReservation(): void {
  sessionStorage.removeItem(RESERVATION_KEY)
}
