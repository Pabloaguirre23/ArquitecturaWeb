import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getPreferences, getRecommendations, savePreferences } from '../api/recommendations'
import type { Preferences, RecommendationItem } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { Loading } from '../components/Loading'
import { RecommendationCard } from '../components/RecommendationCard'
import { errorMessage } from '../utils/errors'
import { EVENT_TYPE_OPTIONS } from '../utils/eventTypes'

export function RecommendationsPage() {
  const { user } = useAuth()
  const [recommendations, setRecommendations] = useState<RecommendationItem[]>([])
  const [preferences, setPreferences] = useState<Preferences | null>(null)
  const [tipos, setTipos] = useState<string[]>([])
  const [artistas, setArtistas] = useState('')
  const [ciudad, setCiudad] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    // El gateway solo habilita /recommendations para CLIENT; para cualquier
    // otro rol autenticado la llamada está condenada a un 403.
    if (user?.role !== 'CLIENT') {
      setLoading(false)
      setRecommendations([])
      return
    }

    let cancelled = false
    setLoading(true)
    setError(null)

    Promise.all([getRecommendations(), getPreferences()])
      .then(([recomendaciones, preferencias]) => {
        if (!cancelled) {
          setRecommendations(recomendaciones.recomendaciones)
          setPreferences(preferencias)
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
  }, [user?.role])

  useEffect(() => {
    if (!preferences) {
      return
    }
    setTipos(preferences.tipos)
    setArtistas(preferences.artistas.join(', '))
    setCiudad(preferences.ciudad ?? '')
  }, [preferences])

  if (user && user.role !== 'CLIENT') {
    return (
      <EmptyState
        title="Sección para clientes"
        description="Ingresá con una cuenta CLIENT para ver tus recomendaciones y preferencias."
      />
    )
  }

  // Los tipos son un filtro inmediato sobre la lista ya rankeada por el backend/mock.
  // El resto de las preferencias (artistas/ciudad) sigue participando del ranking.
  const visibles =
    tipos.length === 0
      ? recommendations
      : recommendations.filter((item) =>
          tipos.some((tipo) => tipo.toLowerCase() === item.tipo.toLowerCase()),
        )

  function toggleTipo(valor: string) {
    setSaved(false)
    setSaveError(null)
    setTipos((current) =>
      current.includes(valor)
        ? current.filter((tipo) => tipo !== valor)
        : [...current, valor],
    )
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaveError(null)
    setSaved(false)

    if (!ciudad.trim()) {
      setSaveError('La ciudad es obligatoria para personalizar las recomendaciones.')
      return
    }

    setSaving(true)
    try {
      const savedPreferences = await savePreferences({
        tipos,
        artistas: artistas
          .split(',')
          .map((value) => value.trim())
          .filter(Boolean),
        ciudad: ciudad.trim(),
      })
      setPreferences(savedPreferences)
      setSaved(true)

      try {
        const result = await getRecommendations()
        setRecommendations(result.recomendaciones)
      } catch {
        // Las preferencias quedaron guardadas; si falla el recálculo se mantiene la lista anterior.
      }
    } catch (caught) {
      setSaveError(errorMessage(caught))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page-gap">
      <header className="page-head">
        <span className="badge">Para vos</span>
        <h1>Recomendaciones</h1>
        <p className="muted">
          Sugerencias según tus gustos, tu ciudad y lo que compraron otros clientes. Cada
          recomendación te dice por qué te la mostramos.
        </p>
      </header>

      <section className="card preference-card" aria-labelledby="preferences-title">
        <div className="results-head">
          <h2 id="preferences-title">Tus preferencias</h2>
          <span className="muted">Filtrá por tipo y guardá para personalizar el orden</span>
        </div>

        <form className="preferences-form" onSubmit={handleSave}>
          <fieldset className="field preference-types">
            <legend>Filtrar por tipo</legend>
            <div className="checkbox-row">
              {EVENT_TYPE_OPTIONS.map((option) => (
                <label key={option.value}>
                  <input
                    type="checkbox"
                    checked={tipos.includes(option.value)}
                    onChange={() => toggleTipo(option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </fieldset>

          <div className="field">
            <label htmlFor="preference-artistas">Artistas favoritos</label>
            <input
              id="preference-artistas"
              value={artistas}
              placeholder="Los Piojos, Callejeros"
              onChange={(event) => {
                setArtistas(event.target.value)
                setSaved(false)
                setSaveError(null)
              }}
            />
            <span className="hint">Separá los nombres con comas.</span>
          </div>

          <div className="field">
            <label htmlFor="preference-ciudad">Ciudad</label>
            <input
              id="preference-ciudad"
              value={ciudad}
              placeholder="Tandil"
              aria-invalid={Boolean(saveError)}
              aria-describedby={saveError ? 'preferences-error' : undefined}
              onChange={(event) => {
                setCiudad(event.target.value)
                setSaved(false)
                setSaveError(null)
              }}
            />
          </div>

          <button className="btn btn-primary" type="submit" disabled={saving}>
            {saving ? 'Guardando…' : 'Guardar preferencias'}
          </button>
        </form>

        {saveError && (
          <p id="preferences-error" className="form-error" role="alert">
            {saveError}
          </p>
        )}
        {saved && (
          <p className="form-success" role="status">
            Preferencias guardadas. Actualizamos tus recomendaciones.
          </p>
        )}
      </section>

      <div className="results-head">
        <h2>Para vos</h2>
        <div className="results-actions">
          {tipos.length > 0 && (
            <button
              className="btn btn-ghost btn-small"
              type="button"
              onClick={() => {
                setTipos([])
                setSaved(false)
                setSaveError(null)
              }}
            >
              Ver todos
            </button>
          )}
          <span className="muted" aria-live="polite">
            {visibles.length} sugerencia{visibles.length === 1 ? '' : 's'}
          </span>
        </div>
      </div>

      {loading && <Loading label="Armando tus recomendaciones…" />}

      {!loading && error && (
        <ErrorState message={error} onRetry={() => window.location.reload()} />
      )}

      {!loading && !error && visibles.length === 0 && (
        <EmptyState
          title={
            recommendations.length === 0
              ? 'Todavía no hay recomendaciones'
              : 'No hay eventos de los tipos elegidos'
          }
          description={
            recommendations.length === 0
              ? 'Probá cargar tus artistas y ciudad favoritos, o volvé más tarde cuando haya más eventos publicados.'
              : 'Probá con otro tipo o tocá “Ver todos” para quitar el filtro.'
          }
          actionLabel={recommendations.length === 0 ? 'Ver eventos' : undefined}
        />
      )}

      {!loading && !error && visibles.length > 0 && (
        <div className="event-grid">
          {visibles.map((item) => (
            <RecommendationCard key={item.eventoId} item={item} />
          ))}
        </div>
      )}
    </div>
  )
}
