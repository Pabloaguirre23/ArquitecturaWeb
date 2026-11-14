import { USE_MOCKS } from '../config'
import { post } from './client'
import { mockLogin } from './mockData'
import type { LoginResponse } from './types'

export async function login(email: string, password: string): Promise<LoginResponse> {
  if (USE_MOCKS) {
    return mockLogin(email, password)
  }
  return post<LoginResponse>('/auth/login', { email, password })
}
