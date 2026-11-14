import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { searchEvents } from '../api/events'
import type { EventSummary, PageResponse, SearchParams } from '../api/types'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { EventCard } from '../components/EventCard'
import { Loading } from '../components/Loading'
import { errorMessage } from '../utils/errors'

interface Filters {
  q: string
  tipo: string
  ciudad: string
  fechaDesde: string
  fechaHasta: string
  sort: string
}

const initialFilters: Filters = {
  q: '',
  tipo: '',
  ciudad: '',
  fechaDesde: '',
  fechaHasta: '',
  sort: 'FECHA_ASC',
}

export function SearchPage() {
  const [filters, setFilters] = useState<Filters>(initialFilters)
  const [query, setQuery] = useState<SearchParams>({ page: 0, size: 6, sort: 'FECHA_ASC' })
  const [data, setData] = useState<PageResponse<EventSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    searchEvents(query)
      .then((result) => {
        if (!cancelled) {
          setData(result)
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(errorMessage(caught))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [query])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setQuery({
      q: filters.q.trim() || undefined,
      tipo: filters.tipo || undefined,
      ciudad: filters.ciudad.trim() || undefined,
      fechaDesde: filters.fechaDesde || undefined,
      fechaHasta: filters.fechaHasta || undefined,
      sort: filters.sort,
      page: 0,
      size: 6,
    })
  }

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((current) => ({ ...current, [key]: value }))
  }

  function goToPage(page: number) {
    setQuery((current) => ({ ...current, page }))
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' })
  }

  const totalLabel =
    data && data.totalElements > 0
      ? `${data.totalElements} evento${data.totalElements === 1 ? '' : 's'} para vos`
      : 'Sin resultados'

  return (
    <>
      <section className="hero">
        <span className="badge">Ticketera virtual</span>
        <h1>Encontrá tu próxima entrada.</h1>
        <p>
          Recitales, teatro, deportes y conferencias. Elegí tu lugar, pagá y llevá tu QR en el
          celular.
        </p>
      </section>

      <section className="search-panel" aria-label="Buscador de eventos">
        <form className="filters" role="search" onSubmit={handleSubmit}>
          <div className="field field-wide">
            <label htmlFor="q">Artista, evento o lugar</label>
            <input
              id="q"
              value={filters.q}
              onChange={(event) => updateFilter('q', event.target.value)}
              placeholder="Ej. Festival de Rock"
            />
          </div>

          <div className="field">
            <label htmlFor="tipo">Tipo</label>
            <select
              id="tipo"
              value={filters.tipo}
              onChange={(event) => updateFilter('tipo', event.target.value)}
            >
              <option value="">Todos</option>
              <option value="RECITAL">Recital</option>
              <option value="TEATRO">Teatro</option>
              <option value="DEPORTE">Deporte</option>
              <option value="CONFERENCIA">Conferencia</option>
            </select>
          </div>

          <div className="field">
            <label htmlFor="ciudad">Ciudad</label>
            <input
              id="ciudad"
              value={filters.ciudad}
              onChange={(event) => updateFilter('ciudad', event.target.value)}
              placeholder="Tandil"
            />
          </div>

          <div className="field">
            <label htmlFor="fechaDesde">Desde</label>
            <input
              id="fechaDesde"
              type="date"
              value={filters.fechaDesde}
              onChange={(event) => updateFilter('fechaDesde', event.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="fechaHasta">Hasta</label>
            <input
              id="fechaHasta"
              type="date"
              value={filters.fechaHasta}
              onChange={(event) => updateFilter('fechaHasta', event.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="sort">Orden</label>
            <select
              id="sort"
              value={filters.sort}
              onChange={(event) => updateFilter('sort', event.target.value)}
            >
              <option value="FECHA_ASC">Fecha</option>
              <option value="PRECIO_ASC">Menor precio</option>
              <option value="PRECIO_DESC">Mayor precio</option>
              <option value="RELEVANCIA">Relevancia</option>
            </select>
          </div>

          <button className="btn btn-primary" type="submit">
            Buscar
          </button>
        </form>
      </section>

      <div className="results-head">
        <h2>Eventos</h2>
        <span className="muted" aria-live="polite">{totalLabel}</span>
      </div>

      {loading && <Loading label="Buscando eventos…" />}

      {!loading && error && (
        <ErrorState message={error} onRetry={() => setQuery((current) => ({ ...current }))} />
      )}

      {!loading && !error && data && data.content.length === 0 && (
        <EmptyState
          title="No encontramos eventos"
          description="Probá con otros filtros o fechas. También podés limpiar la búsqueda."
          actionLabel="Limpiar filtros"
          onAction={() => {
            setFilters(initialFilters)
            setQuery({ page: 0, size: 6, sort: 'FECHA_ASC' })
          }}
        />
      )}

      {!loading && !error && data && data.content.length > 0 && (
        <>
          <div className="event-grid">
            {data.content.map((event) => (
              <EventCard key={event.id} event={event} />
            ))}
          </div>

          {data.totalPages > 1 && (
            <nav className="pager" aria-label="Paginación de eventos">
              <button
                className="btn btn-ghost btn-small"
                type="button"
                disabled={data.page <= 0}
                onClick={() => goToPage(data.page - 1)}
              >
                Anterior
              </button>
              <span className="muted">
                Página {data.page + 1} de {data.totalPages}
              </span>
              <button
                className="btn btn-ghost btn-small"
                type="button"
                disabled={data.page + 1 >= data.totalPages}
                onClick={() => goToPage(data.page + 1)}
              >
                Siguiente
              </button>
            </nav>
          )}
        </>
      )}
    </>
  )
}
