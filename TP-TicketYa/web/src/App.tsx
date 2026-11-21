import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Layout } from './components/Layout'
import { SessionNotice } from './components/SessionNotice'
import { AccessPage } from './pages/AccessPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { EventDetailPage } from './pages/EventDetailPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RecommendationsPage } from './pages/RecommendationsPage'
import { ReportsPage } from './pages/ReportsPage'
import { SearchPage } from './pages/SearchPage'
import { TicketsPage } from './pages/TicketsPage'

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <SessionNotice />
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<Layout />}>
            <Route index element={<SearchPage />} />
            <Route path="events/:id" element={<EventDetailPage />} />
            <Route element={<ProtectedRoute />}>
              <Route path="checkout" element={<CheckoutPage />} />
              <Route path="tickets" element={<TicketsPage />} />
              <Route path="para-vos" element={<RecommendationsPage />} />
              <Route path="access" element={<AccessPage />} />
              <Route path="reports" element={<ReportsPage />} />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
