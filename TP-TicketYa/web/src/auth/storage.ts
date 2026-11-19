export interface AuthUser {
  id?: string
  email: string
  role: string
  nombre?: string
}

export interface StoredAuth {
  token: string
  user: AuthUser
}

export const AUTH_STORAGE_KEY = 'ticketya.auth'

export function readAuth(): StoredAuth | null {
  const raw = sessionStorage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as StoredAuth
  } catch {
    sessionStorage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

export function writeAuth(auth: StoredAuth): void {
  sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth))
}

export function clearAuth(): void {
  sessionStorage.removeItem(AUTH_STORAGE_KEY)
}
