import { describe, expect, it } from 'vitest'
import { mockLogin } from './mockData'

describe('mockLogin', () => {
  it('asigna el rol correcto a los cuatro usuarios demo', async () => {
    const casos: Array<[string, string]> = [
      ['cliente@ticketya.test', 'CLIENT'],
      ['organizador@ticketya.test', 'ORGANIZER'],
      ['admin@ticketya.test', 'ADMIN'],
      ['acceso@ticketya.test', 'ACCESS_STAFF'],
    ]

    for (const [email, rol] of casos) {
      const resultado = await mockLogin(email, 'demo')
      expect(resultado.role, email).toBe(rol)
    }
  })
})
