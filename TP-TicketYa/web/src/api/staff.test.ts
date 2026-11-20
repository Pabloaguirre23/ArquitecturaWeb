import { describe, expect, it } from 'vitest'
import { mockAccessValidation, mockEventReport } from './mockData'

describe('staff', () => {
  it('valida un QR libre y devuelve la entrada utilizada', async () => {
    const resultado = await mockAccessValidation('qr-libre', 'd0000000-0000-4000-8000-000000000001')
    expect(resultado.estado).toBe('UTILIZADA')
    expect(resultado.eventoId).toBe('d0000000-0000-4000-8000-000000000001')
    expect(resultado.usedAt).toBeTruthy()
  })

  it('rechaza un QR ya usado con 409', async () => {
    await expect(mockAccessValidation('usado-123', 'evento')).rejects.toMatchObject({
      status: 409,
      code: 'TICKET_ALREADY_USED',
    })
  })

  it('rechaza un QR de otro evento con 409', async () => {
    await expect(mockAccessValidation('otro-123', 'evento')).rejects.toMatchObject({
      status: 409,
      code: 'TICKET_WRONG_EVENT',
    })
  })

  it('rechaza un QR inexistente con 404', async () => {
    await expect(mockAccessValidation('no-123', 'evento')).rejects.toMatchObject({
      status: 404,
      code: 'TICKET_NOT_FOUND',
    })
  })

  it('calcula el reporte con totales coherentes', async () => {
    const reporte = await mockEventReport('d0000000-0000-4000-8000-000000000001')
    expect(reporte.sectores).toHaveLength(2)
    expect(reporte.totales.vendidas).toBe(120)
    expect(reporte.totales.devueltas).toBe(5)
    expect(reporte.totales.netas).toBe(115)
    expect(reporte.datosDesactualizados).toBe(false)
  })
})
