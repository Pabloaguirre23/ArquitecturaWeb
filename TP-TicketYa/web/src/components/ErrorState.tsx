export function ErrorState({
  message,
  onRetry,
}: {
  message: string
  onRetry?: () => void
}) {
  return (
    <div className="error-box" role="alert">
      <strong>No pudimos completar la operación.</strong>
      <p>{message}</p>
      {onRetry && (
        <button className="btn btn-secondary btn-small" type="button" onClick={onRetry}>
          Reintentar
        </button>
      )}
    </div>
  )
}
