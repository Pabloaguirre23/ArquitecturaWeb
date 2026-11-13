export type TipoEvento = 'RECITAL' | 'TEATRO' | 'DEPORTE' | 'CONFERENCIA' | string
export type EstadoEvento = 'BORRADOR' | 'PUBLICADO' | 'AGOTADO' | 'CANCELADO' | 'FINALIZADO'
export type TipoSector = 'NUMERADO' | 'NO_NUMERADO'
export type EstadoAsiento = 'LIBRE' | 'RESERVADO' | 'VENDIDO'
export type MedioPago = 'TARJETA' | 'EFECTIVO' | 'TRANSFERENCIA'
export type EstadoOrden = 'PENDIENTE_PAGO' | 'PAGADA' | 'RECHAZADA' | 'ERROR_PAGO' | 'REINTEGRO_PENDIENTE'

export interface EventSummary {
  id: string
  nombre: string
  tipo: TipoEvento
  ciudad: string
  lugarNombre: string
  fecha: string
  estado: EstadoEvento
  precioDesde: number
  altaDemanda?: boolean
}

export interface Sector {
  sectorId: string
  nombre: string
  tipo: TipoSector
  precio: number
  capacidad: number
  disponibles: number
}

export interface EventDetail {
  id: string
  nombre: string
  descripcion: string
  tipo: TipoEvento
  lugarId: string
  lugarNombre: string
  ciudad: string
  organizadorId: string
  fecha: string
  estado: EstadoEvento
  limitePorCliente: number
  participantes: string[]
  sectores: Sector[]
  altaDemanda?: boolean
}

export interface Seat {
  asientoId: string
  fila: number
  numero: number
  estado: EstadoAsiento
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface SearchParams {
  q?: string
  tipo?: string
  ciudad?: string
  fechaDesde?: string
  fechaHasta?: string
  page?: number
  size?: number
  sort?: string
}

export interface CreateReservationItem {
  sectorId: string
  asientoIds?: string[]
  cantidad?: number
}

export interface ReservationItem {
  sectorId: string
  asientoId?: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface Reservation {
  id: string
  eventoId: string
  eventoNombre: string
  eventoFecha: string
  clienteId: string
  estado: string
  expiresAt: string
  items: ReservationItem[]
  total: number
}

export interface CreateOrderRequest {
  reservaId: string
  medioPago: MedioPago
  token?: string
}

export interface Ticket {
  id: string
  eventoId: string
  eventoNombre: string
  eventoFecha: string
  sectorId: string
  asientoId: string | null
  qrCode: string
  estado: string
  createdAt?: string
}

export interface Order {
  id: string
  clienteId: string
  reservaId: string
  eventoId: string
  eventoNombre: string
  eventoFecha: string
  estado: EstadoOrden
  medioPago: MedioPago
  total: number
  items: Array<{
    sectorId: string
    asientoId: string | null
    cantidad: number
    precioUnitario: number
    subtotal: number
  }>
  entradas: Ticket[]
  createdAt: string
}

export interface LoginResponse {
  token: string
  role: string
  email: string
  expiresAt?: string
  nombre?: string
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  errors?: string[]
}
