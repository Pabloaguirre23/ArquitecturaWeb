import { useMemo } from 'react'

function hash(value: string): number {
  let result = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index)
    result = Math.imul(result, 16777619)
  }
  return result >>> 0
}

export function QrPlaceholder({ value }: { value: string }) {
  const cells = useMemo(() => {
    const seed = hash(value)
    return Array.from({ length: 21 * 21 }, (_, index) => {
      const mixed = (seed ^ Math.imul(index + 1, 2654435761)) >>> 0
      return mixed % 7 < 3
    })
  }, [value])

  return (
    <div className="qr" role="img" aria-label={`Código QR de la entrada ${value}`}>
      {cells.map((on, index) => (
        <i key={index} className={on ? 'on' : ''} />
      ))}
    </div>
  )
}
