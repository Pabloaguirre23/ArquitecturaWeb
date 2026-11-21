import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { login as loginRequest } from '../api/auth'
import { AuthContext } from './auth-context'
import { clearAuth, readAuth, writeAuth } from './storage'
import type { StoredAuth } from './storage'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<StoredAuth | null>(() => readAuth())

  const login = useCallback(async (email: string, password: string) => {
    const response = await loginRequest(email, password)
    const stored: StoredAuth = {
      token: response.token,
      user: {
        email: response.email,
        role: response.role,
        nombre: response.nombre,
      },
    }
    writeAuth(stored)
    setAuth(stored)
    return stored.user
  }, [])

  const logout = useCallback(() => {
    clearAuth()
    // Los datos de demo y la reserva en curso son de la sesión que se cierra.
    // Sin esto, al entrar con otro usuario se veían entradas o preferencias ajenas.
    for (const key of [
      'ticketya.reservation',
      'ticketya.mock.reservation',
      'ticketya.mock.tickets',
      'ticketya.mock.preferences',
    ]) {
      sessionStorage.removeItem(key)
    }
    setAuth(null)
  }, [])

  useEffect(() => {
    const handleUnauthorized = () => logout()
    window.addEventListener('ticketya:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('ticketya:unauthorized', handleUnauthorized)
  }, [logout])

  const value = useMemo(
    () => ({
      token: auth?.token ?? null,
      user: auth?.user ?? null,
      isAuthenticated: Boolean(auth?.token),
      login,
      logout,
    }),
    [auth, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
