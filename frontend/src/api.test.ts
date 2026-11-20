import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { login, logout } from './api'

describe('api', () => {
  it('postSendsXsrfHeaderFromCookie', async () => {
    document.cookie = 'XSRF-TOKEN=token-from-cookie; path=/'
    let sentHeader: string | null = null
    let sentCredentials: RequestCredentials | undefined
    server.use(
      http.post('/api/admin/logout', ({ request }) => {
        sentHeader = request.headers.get('X-XSRF-TOKEN')
        sentCredentials = request.credentials
        return new HttpResponse(null, { status: 200 })
      }),
    )

    await logout()

    expect(sentHeader).toBe('token-from-cookie')
    expect(sentCredentials).toBe('same-origin')
  })

  it('loginReturnsFalseOn401', async () => {
    document.cookie = 'XSRF-TOKEN=abc; path=/'
    server.use(
      http.post('/api/admin/login', () => new HttpResponse(null, { status: 401 })),
    )

    expect(await login('admin', 'wrong')).toBe(false)
  })
})
