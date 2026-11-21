import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { errorCode, errorMessage } from './errors'

describe('errores de la UI', () => {
  it('traduce QUEUE_REQUIRED a una explicación accionable', () => {
    const error = new ApiError(403, 'Este evento exige un token de fila vigente', 'QUEUE_REQUIRED')

    expect(errorCode(error)).toBe('QUEUE_REQUIRED')
    expect(errorMessage(error)).toContain('fila virtual')
    expect(errorMessage(error)).not.toContain('Este evento exige un token')
  })

  it('traduce la reserva vencida y conserva los códigos desconocidos', () => {
    expect(errorMessage(new ApiError(410, 'La reserva está vencida', 'RESERVATION_EXPIRED')))
      .toContain('Elegí tus lugares de nuevo')
    expect(errorMessage(new ApiError(409, 'Mensaje del backend', 'OTRO_CODIGO')))
      .toBe('Mensaje del backend')
  })
})
