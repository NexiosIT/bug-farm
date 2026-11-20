import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { setMockLoggedIn } from './mocks/handlers'
import { getSettings } from './api'
import { SettingsPage } from './SettingsPage'

async function renderSettings() {
  render(<SettingsPage onUnauthorized={vi.fn()} />)
  const cutOff = await screen.findByLabelText('Cut-off time')
  await vi.waitFor(() => expect(cutOff).toHaveValue('10:00'))
  return { cutOff, shopEmail: screen.getByLabelText('Shop email address') }
}

describe('SettingsPage', () => {
  beforeEach(() => setMockLoggedIn(true))

  it('savesValues', async () => {
    const user = userEvent.setup()
    const { cutOff, shopEmail } = await renderSettings()
    expect(shopEmail).toHaveValue('orders@sandwichshop.example')

    await user.clear(cutOff)
    await user.type(cutOff, '10:30')
    await user.clear(shopEmail)
    await user.type(shopEmail, 'lunch@shop.example')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Saved.')
    expect(await getSettings()).toEqual({ cutOff: '10:30', shopEmail: 'lunch@shop.example' })
  })

  it('showsServerErrorOn400', async () => {
    const user = userEvent.setup()
    let putCalls = 0
    server.use(
      http.put('/api/admin/settings', () => {
        putCalls++
        return HttpResponse.json({ error: 'cutOff: must be HH:mm' }, { status: 400 })
      }),
    )
    const { cutOff } = await renderSettings()

    await user.clear(cutOff)
    await user.type(cutOff, '25:00')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('cutOff: must be HH:mm')
    expect(putCalls).toBe(1)
    expect(screen.queryByText('Saved.')).not.toBeInTheDocument()
  })

  it('badEmailReachesServerAndShowsItsError', async () => {
    const user = userEvent.setup()
    const { shopEmail } = await renderSettings()

    await user.clear(shopEmail)
    await user.type(shopEmail, 'not-an-email')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('shopEmail: must be a valid e-mail address')
    expect((await getSettings()).shopEmail).toBe('orders@sandwichshop.example')
  })
})
