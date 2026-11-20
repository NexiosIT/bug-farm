import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import App from './App'

describe('App', () => {
  it('showsLoginWhenLoggedOut', async () => {
    render(<App />)

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
  })

  it('showsTabsWhenLoggedIn', async () => {
    server.use(http.get('/api/admin/me', () => HttpResponse.json({ username: 'admin' })))
    render(<App />)

    const tabs = await screen.findAllByRole('tab')
    expect(tabs.map((t) => t.textContent)).toEqual(["Today's orders", 'Menu', 'Settings'])
  })

  it('showsTabsAfterLoggingIn', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(await screen.findByLabelText('Username'), 'admin')
    await user.type(screen.getByLabelText('Password'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('tab', { name: 'Menu' })).toBeInTheDocument()
  })
})
