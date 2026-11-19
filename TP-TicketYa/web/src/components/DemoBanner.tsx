export function DemoBanner() {
  return (
    <div className="demo-banner">
      <div className="container">
        <strong>Modo demo:</strong> datos de ejemplo en memoria. Para usar el gateway real,
        ejecutá la app con <code>VITE_USE_MOCKS=false</code>.
      </div>
    </div>
  )
}
