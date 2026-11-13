const env = import.meta.env

export const API_BASE_URL = (env.VITE_API_URL ?? '/api').replace(/\/$/, '')
export const USE_MOCKS = (env.VITE_USE_MOCKS ?? 'true') !== 'false'
export const DEMO_EMAIL = env.VITE_DEMO_EMAIL ?? 'cliente@ticketya.test'
export const DEMO_PASSWORD = env.VITE_DEMO_PASSWORD ?? 'demo'
