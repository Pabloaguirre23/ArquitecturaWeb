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
  }, [])

  const logout = useCallback(() => {
    clearAuth()
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
