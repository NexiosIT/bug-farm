import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoginPage } from './LoginPage'

async function fillAndSubmit(username: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('LoginPage', () => {
  it('showsErrorOnWrongPassword', async () => {
    const onLoggedIn = vi.fn()
    render(<LoginPage onLoggedIn={onLoggedIn} />)

    await fillAndSubmit('admin', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong username or password.')
    expect(onLoggedIn).not.toHaveBeenCalled()
  })

  it('callsOnLoggedInAfterSuccess', async () => {
    const onLoggedIn = vi.fn()
    render(<LoginPage onLoggedIn={onLoggedIn} />)

    await fillAndSubmit('admin', 'secret')

    await vi.waitFor(() => expect(onLoggedIn).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
