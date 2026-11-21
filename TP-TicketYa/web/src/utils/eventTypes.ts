export const EVENT_TYPE_OPTIONS = [
  { value: 'RECITAL', label: 'Recital' },
  { value: 'TEATRO', label: 'Teatro' },
  { value: 'DEPORTE', label: 'Deporte' },
  { value: 'CONFERENCIA', label: 'Conferencia' },
  { value: 'FESTIVAL', label: 'Festival' },
  { value: 'OTRO', label: 'Otro' },
] as const

export function eventTypeLabel(tipo: string): string {
  return EVENT_TYPE_OPTIONS.find((option) => option.value === tipo)?.label ?? tipo
}
