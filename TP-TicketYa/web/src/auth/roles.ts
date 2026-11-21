/** Ruta inicial según el rol: cada uno aterriza en el servicio que le corresponde. */
export function homeForRole(role?: string): string {
  switch (role) {
    case 'ORGANIZER':
    case 'ADMIN':
      return '/reports'
    case 'ACCESS_STAFF':
      return '/access'
    default:
      return '/'
  }
}

export function roleLabel(role?: string): string {
  switch (role) {
    case 'CLIENT':
      return 'Cliente'
    case 'ORGANIZER':
      return 'Organizador'
    case 'ADMIN':
      return 'Admin'
    case 'ACCESS_STAFF':
      return 'Acceso'
    default:
      return 'Invitado'
  }
}
