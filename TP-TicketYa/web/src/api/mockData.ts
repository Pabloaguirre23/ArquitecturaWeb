import { ApiError } from './client'
import { readAuth } from '../auth/storage'
import type {
  AccessValidation,
  CreateOrderRequest,
  CreateReservationItem,
  EventDetail,
  EventReport,
  EventSummary,
  LoginResponse,
  Order,
  PageResponse,
  Reservation,
  ReservationItem,
  Seat,
  SearchParams,
  Ticket,
} from './types'

const MOCK_RESERVATION_KEY = 'ticketya.mock.reservation'
const MOCK_TICKETS_KEY = 'ticketya.mock.tickets'

const FESTIVAL_ID = 'd0000000-0000-4000-8000-000000000001'
const TEATRO_ID = 'd0000000-0000-4000-8000-000000000002'
const CHARLA_ID = 'd0000000-0000-4000-8000-000000000003'
const DEPORTE_ID = 'd0000000-0000-4000-8000-000000000004'
const RECITAL_ID = 'd0000000-0000-4000-8000-000000000005'

export const MOCK_EVENTS: EventDetail[] = [
  {
    id: FESTIVAL_ID,
    nombre: 'Festival de Rock Tandil 2027',
    descripcion:
      'Una noche de rock con bandas locales e invitados nacionales. Puertas abiertas desde las 19:00, show principal a las 21:00.',
    tipo: 'RECITAL',
    lugarId: 'a0000000-0000-4000-8000-000000000001',
    lugarNombre: 'Estadio Municipal de Tandil',
    ciudad: 'Tandil',
    organizadorId: '22222222-2222-4222-8222-222222222222',
    fecha: '2027-03-14T21:00:00-03:00',
    estado: 'PUBLICADO',
    limitePorCliente: 4,
    participantes: ['Los Piojos', 'Babasónicos', 'La Vela Puerca'],
    altaDemanda: true,
    sectores: [
      {
        sectorId: 'b0000000-0000-4000-8000-000000000001',
        nombre: 'Campo',
        tipo: 'NO_NUMERADO',
        precio: 25000,
        capacidad: 5000,
        disponibles: 4321,
      },
      {
        sectorId: 'b0000000-0000-4000-8000-000000000002',
        nombre: 'Platea',
        tipo: 'NUMERADO',
        precio: 45000,
        capacidad: 300,
        disponibles: 245,
      },
    ],
  },
  {
    id: TEATRO_ID,
    nombre: 'El Avaro — Teatro Universitario',
    descripcion:
      'Comedia clásica de Molière en una puesta universitaria. Una obra sobre el dinero, la obsesión y las apariencias.',
    tipo: 'TEATRO',
    lugarId: 'a0000000-0000-4000-8000-000000000002',
    lugarNombre: 'Complejo Cultural Universitario',
    ciudad: 'Tandil',
    organizadorId: '22222222-2222-4222-8222-222222222222',
    fecha: '2027-04-18T20:30:00-03:00',
    estado: 'PUBLICADO',
    limitePorCliente: 4,
    participantes: ['Compañía de Teatro UNICEN'],
    sectores: [
      {
        sectorId: 'b0000000-0000-4000-8000-000000000003',
        nombre: 'Platea',
        tipo: 'NUMERADO',
        precio: 18000,
        capacidad: 120,
        disponibles: 98,
      },
      {
        sectorId: 'b0000000-0000-4000-8000-000000000004',
        nombre: 'Pullman',
        tipo: 'NO_NUMERADO',
        precio: 12000,
        capacidad: 80,
        disponibles: 61,
      },
    ],
  },
  {
    id: CHARLA_ID,
    nombre: 'Arquitectura Web 2027 — Charla abierta',
    descripcion:
      'Conversatorio sobre microservicios, consistencia y observabilidad con docentes y egresados de la carrera.',
    tipo: 'CONFERENCIA',
    lugarId: 'a0000000-0000-4000-8000-000000000002',
    lugarNombre: 'Complejo Cultural Universitario',
    ciudad: 'Tandil',
    organizadorId: '22222222-2222-4222-8222-222222222222',
    fecha: '2027-05-20T18:00:00-03:00',
    estado: 'PUBLICADO',
    limitePorCliente: 2,
    participantes: ['Díaz Pace', 'Yañez'],
    sectores: [
      {
        sectorId: 'b0000000-0000-4000-8000-000000000005',
        nombre: 'General',
        tipo: 'NO_NUMERADO',
        precio: 0,
        capacidad: 200,
        disponibles: 174,
      },
    ],
  },
  {
    id: DEPORTE_ID,
    nombre: 'Clásico de básquet: UNICEN vs. Estudiantes',
    descripcion:
      'El clásico universitario de la ciudad. Entradas numeradas para la platea y generales para la popular.',
    tipo: 'DEPORTE',
    lugarId: 'a0000000-0000-4000-8000-000000000003',
    lugarNombre: 'Gimnasio Duggan',
    ciudad: 'Tandil',
    organizadorId: '22222222-2222-4222-8222-222222222222',
    fecha: '2027-06-12T21:30:00-03:00',
    estado: 'PUBLICADO',
    limitePorCliente: 4,
    participantes: ['UNICEN', 'Estudiantes de Tandil'],
    sectores: [
      {
        sectorId: 'b0000000-0000-4000-8000-000000000006',
        nombre: 'Platea',
        tipo: 'NUMERADO',
        precio: 15000,
        capacidad: 180,
        disponibles: 151,
      },
      {
        sectorId: 'b0000000-0000-4000-8000-000000000007',
        nombre: 'Popular',
        tipo: 'NO_NUMERADO',
        precio: 8000,
        capacidad: 400,
        disponibles: 366,
      },
    ],
  },
  {
    id: RECITAL_ID,
    nombre: 'Noche de Rock en el Anfiteatro',
    descripcion:
      'Los Piojos y Callejeros vuelven al anfiteatro municipal con un show a beneficio de la cooperadora.',
    tipo: 'RECITAL',
    lugarId: 'a0000000-0000-4000-8000-000000000004',
    lugarNombre: 'Anfiteatro Municipal',
    ciudad: 'Tandil',
    organizadorId: '22222222-2222-4222-8222-222222222222',
    fecha: '2027-07-03T20:00:00-03:00',
    estado: 'PUBLICADO',
    limitePorCliente: 4,
    participantes: ['Los Piojos', 'Callejeros'],
    altaDemanda: true,
    sectores: [
      {
        sectorId: 'b0000000-0000-4000-8000-000000000008',
        nombre: 'Campo',
        tipo: 'NO_NUMERADO',
        precio: 22000,
        capacidad: 3500,
        disponibles: 2804,
      },
    ],
  },
]

const seatCache = new Map<string, Seat[]>()

function toSummary(event: EventDetail): EventSummary {
  const precioDesde = event.sectores.reduce(
    (min, sector) => Math.min(min, sector.precio),
    Number.POSITIVE_INFINITY,
  )
  return {
    id: event.id,
    nombre: event.nombre,
    tipo: event.tipo,
    ciudad: event.ciudad,
    lugarNombre: event.lugarNombre,
    fecha: event.fecha,
    estado: event.estado,
    precioDesde: Number.isFinite(precioDesde) ? precioDesde : 0,
    altaDemanda: event.altaDemanda,
  }
}

export function mockSearch(params: SearchParams): PageResponse<EventSummary> {
  const q = params.q?.trim().toLowerCase()
  const filtered = MOCK_EVENTS.filter((event) => {
    if (q && !`${event.nombre} ${event.descripcion} ${event.lugarNombre}`.toLowerCase().includes(q)) {
      return false
    }
    if (params.tipo && event.tipo !== params.tipo) {
      return false
    }
    if (params.ciudad && !event.ciudad.toLowerCase().includes(params.ciudad.toLowerCase())) {
      return false
    }
    if (params.fechaDesde && new Date(event.fecha) < new Date(params.fechaDesde)) {
      return false
    }
    if (params.fechaHasta && new Date(event.fecha) > new Date(`${params.fechaHasta}T23:59:59`)) {
      return false
    }
    return true
  }).sort((a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime())

  const page = params.page ?? 0
  const size = params.size ?? 6
  const start = page * size
  const content = filtered.slice(start, start + size).map(toSummary)

  return {
    content,
    page,
    size,
    totalElements: filtered.length,
    totalPages: Math.max(1, Math.ceil(filtered.length / size)),
  }
}

export function mockEvent(eventId: string): EventDetail {
  const event = MOCK_EVENTS.find((candidate) => candidate.id === eventId)
  if (!event) {
    throw new ApiError(404, 'El evento no existe o no está publicado.', 'EVENT_NOT_FOUND')
  }
  return event
}

export function mockSeats(eventId: string, sectorId: string): Seat[] {
  const event = mockEvent(eventId)
  const sector = event.sectores.find((candidate) => candidate.sectorId === sectorId)
  if (!sector || sector.tipo !== 'NUMERADO') {
    return []
  }

  const cacheKey = `${eventId}:${sectorId}`
  const cached = seatCache.get(cacheKey)
  if (cached) {
    return cached
  }

  const perRow = 15
  const seats = Array.from({ length: sector.capacidad }, (_, index) => {
    const numero = (index % perRow) + 1
    const fila = Math.floor(index / perRow) + 1
    const estado = index % 13 === 0 ? 'VENDIDO' : index % 7 === 0 ? 'RESERVADO' : 'LIBRE'
    return {
      asientoId: crypto.randomUUID(),
      fila,
      numero,
      estado,
    } satisfies Seat
  })

  seatCache.set(cacheKey, seats)
  return seats
}

export function mockReservation(eventId: string, items: CreateReservationItem[]): Reservation {
  const event = mockEvent(eventId)
  const reservationItems: ReservationItem[] = []

  for (const item of items) {
    const sector = event.sectores.find((candidate) => candidate.sectorId === item.sectorId)
    if (!sector) {
      throw new ApiError(400, 'El sector no pertenece al evento.', 'SECTOR_NOT_FROM_EVENT')
    }

    if (item.asientoIds?.length) {
      for (const asientoId of item.asientoIds) {
        reservationItems.push({
          sectorId: sector.sectorId,
          asientoId,
          cantidad: 1,
          precioUnitario: sector.precio,
          subtotal: sector.precio,
        })
      }
    } else {
      const cantidad = item.cantidad ?? 1
      reservationItems.push({
        sectorId: sector.sectorId,
        cantidad,
        precioUnitario: sector.precio,
        subtotal: sector.precio * cantidad,
      })
    }
  }

  if (reservationItems.length === 0) {
    throw new ApiError(400, 'Tenés que elegir al menos un lugar.', 'EMPTY_RESERVATION')
  }

  const total = reservationItems.reduce((sum, item) => sum + item.subtotal, 0)
  const reservation: Reservation = {
    id: crypto.randomUUID(),
    eventoId: event.id,
    eventoNombre: event.nombre,
    eventoFecha: event.fecha,
    clienteId: readAuth()?.user.id ?? 'demo-client',
    estado: 'VIGENTE',
    expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    items: reservationItems,
    total,
  }

  sessionStorage.setItem(MOCK_RESERVATION_KEY, JSON.stringify(reservation))
  return reservation
}

function readStoredReservation(reservaId: string): Reservation {
  const raw = sessionStorage.getItem(MOCK_RESERVATION_KEY)
  if (!raw) {
    throw new ApiError(404, 'La reserva no existe o venció.', 'RESERVATION_NOT_FOUND')
  }
  const reservation = JSON.parse(raw) as Reservation
  if (reservation.id !== reservaId) {
    throw new ApiError(404, 'La reserva no existe o venció.', 'RESERVATION_NOT_FOUND')
  }
  return reservation
}

function readStoredTickets(): Ticket[] {
  const raw = sessionStorage.getItem(MOCK_TICKETS_KEY)
  if (!raw) {
    return []
  }
  try {
    return JSON.parse(raw) as Ticket[]
  } catch {
    return []
  }
}

function writeStoredTickets(tickets: Ticket[]): void {
  sessionStorage.setItem(MOCK_TICKETS_KEY, JSON.stringify(tickets))
}

function seedTickets(): Ticket[] {
  const existing = readStoredTickets()
  if (existing.length > 0) {
    return existing
  }

  const seeds: Ticket[] = [
    {
      id: crypto.randomUUID(),
      eventoId: TEATRO_ID,
      eventoNombre: 'El Avaro — Teatro Universitario',
      eventoFecha: '2027-04-18T20:30:00-03:00',
      sectorId: 'b0000000-0000-4000-8000-000000000003',
      asientoId: 'c0000000-0000-4000-8000-000000000101',
      qrCode: crypto.randomUUID(),
      estado: 'VALIDA',
      createdAt: new Date().toISOString(),
    },
  ]
  writeStoredTickets(seeds)
  return seeds
}

export async function mockLogin(email: string, password: string): Promise<LoginResponse> {
  await new Promise((resolve) => window.setTimeout(resolve, 450))
  if (!email.trim() || !password.trim()) {
    throw new ApiError(400, 'Email y contraseña son obligatorios.', 'VALIDATION_ERROR')
  }
  const normalized = email.trim().toLowerCase()
  const demo = [
    { prefijo: 'admin', role: 'ADMIN', nombre: 'Admin Demo' },
    { prefijo: 'organizador', role: 'ORGANIZER', nombre: 'Organizador Demo' },
    { prefijo: 'acceso', role: 'ACCESS_STAFF', nombre: 'Acceso Demo' },
    { prefijo: 'cliente', role: 'CLIENT', nombre: 'Cliente Demo' },
  ].find((usuario) => normalized.startsWith(usuario.prefijo))
  return {
    token: `demo.${crypto.randomUUID()}`,
    role: demo?.role ?? 'CLIENT',
    email,
    nombre: demo?.nombre ?? email.split('@')[0],
    expiresAt: new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(),
  }
}

export async function mockOrder(request: CreateOrderRequest): Promise<Order> {
  const reservation = readStoredReservation(request.reservaId)

  if (request.token === 'RECHAZADO') {
    await new Promise((resolve) => window.setTimeout(resolve, 600))
    return {
      id: crypto.randomUUID(),
      clienteId: reservation.clienteId,
      reservaId: reservation.id,
      eventoId: reservation.eventoId,
      eventoNombre: reservation.eventoNombre,
      eventoFecha: reservation.eventoFecha,
      estado: 'RECHAZADA',
      medioPago: request.medioPago,
      total: reservation.total,
      items: reservation.items.map((item) => ({
        sectorId: item.sectorId,
        asientoId: item.asientoId ?? null,
        cantidad: item.cantidad,
        precioUnitario: item.precioUnitario,
        subtotal: item.subtotal,
      })),
      entradas: [],
      createdAt: new Date().toISOString(),
    }
  }

  if (request.token === 'LENTO') {
    await new Promise((resolve) => window.setTimeout(resolve, 1800))
  }

  const tickets: Ticket[] = []
  for (const item of reservation.items) {
    const quantity = item.asientoId ? 1 : item.cantidad
    for (let index = 0; index < quantity; index += 1) {
      tickets.push({
        id: crypto.randomUUID(),
        eventoId: reservation.eventoId,
        eventoNombre: reservation.eventoNombre,
        eventoFecha: reservation.eventoFecha,
        sectorId: item.sectorId,
        asientoId: item.asientoId ?? null,
        qrCode: crypto.randomUUID(),
        estado: 'VALIDA',
        createdAt: new Date().toISOString(),
      })
    }
  }

  writeStoredTickets([...seedTickets(), ...tickets])

  return {
    id: crypto.randomUUID(),
    clienteId: reservation.clienteId,
    reservaId: reservation.id,
    eventoId: reservation.eventoId,
    eventoNombre: reservation.eventoNombre,
    eventoFecha: reservation.eventoFecha,
    estado: 'PAGADA',
    medioPago: request.medioPago,
    total: reservation.total,
    items: reservation.items.map((item) => ({
      sectorId: item.sectorId,
      asientoId: item.asientoId ?? null,
      cantidad: item.cantidad,
      precioUnitario: item.precioUnitario,
      subtotal: item.subtotal,
    })),
    entradas: tickets,
    createdAt: new Date().toISOString(),
  }
}

export function mockTickets(): PageResponse<Ticket> {
  const tickets = seedTickets().sort(
    (a, b) => new Date(a.eventoFecha).getTime() - new Date(b.eventoFecha).getTime(),
  )
  return {
    content: tickets,
    page: 0,
    size: tickets.length,
    totalElements: tickets.length,
    totalPages: 1,
  }
}

export function clearMockReservation(): void {
  sessionStorage.removeItem(MOCK_RESERVATION_KEY)
}

export async function mockAccessValidation(qrCode: string, eventoId: string): Promise<AccessValidation> {
  await new Promise((resolve) => window.setTimeout(resolve, 350))
  if (!qrCode.trim() || !eventoId.trim()) {
    throw new ApiError(400, 'El QR y el evento son obligatorios.', 'VALIDATION_ERROR')
  }
  if (qrCode.startsWith('usado-')) {
    throw new ApiError(409, 'La entrada ya fue utilizada.', 'TICKET_ALREADY_USED')
  }
  if (qrCode.startsWith('otro-')) {
    throw new ApiError(409, 'La entrada pertenece a otro evento.', 'TICKET_WRONG_EVENT')
  }
  if (qrCode.startsWith('no-')) {
    throw new ApiError(404, 'No existe una entrada con ese QR.', 'TICKET_NOT_FOUND')
  }
  return {
    entradaId: crypto.randomUUID(),
    ordenId: crypto.randomUUID(),
    eventoId,
    sectorId: crypto.randomUUID(),
    estado: 'UTILIZADA',
    usedAt: new Date().toISOString(),
    usedBy: '55555555-5555-4555-8555-555555555555',
  }
}

export async function mockEventReport(eventoId: string): Promise<EventReport> {
  await new Promise((resolve) => window.setTimeout(resolve, 450))
  if (!eventoId.trim()) {
    throw new ApiError(400, 'El evento es obligatorio.', 'VALIDATION_ERROR')
  }
  const sectores = [
    {
      sectorId: 'b0000000-0000-4000-8000-000000000001',
      sectorNombre: 'Platea A',
      capacidad: 100,
      vendidas: 40,
      devueltas: 5,
      netas: 35,
      ocupacion: 0.35,
      recaudacion: 525000,
    },
    {
      sectorId: 'b0000000-0000-4000-8000-000000000002',
      sectorNombre: 'Campo',
      capacidad: 200,
      vendidas: 80,
      devueltas: 0,
      netas: 80,
      ocupacion: 0.4,
      recaudacion: 960000,
    },
  ]
  return {
    eventoId,
    organizadorId: '22222222-2222-4222-8222-222222222222',
    sectores,
    totales: {
      vendidas: 120,
      devueltas: 5,
      netas: 115,
      recaudacion: 1485000,
    },
    datosDesactualizados: false,
    generadoEn: new Date().toISOString(),
  }
}
