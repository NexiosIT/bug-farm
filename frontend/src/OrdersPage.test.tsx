import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { setMockLoggedIn } from './mocks/handlers'
import { OrdersPage } from './OrdersPage'

describe('OrdersPage', () => {
  beforeEach(() => setMockLoggedIn(true))

  it('listsOrdersFromApi', async () => {
    render(<OrdersPage onUnauthorized={vi.fn()} />)

    expect(await screen.findByRole('heading', { name: 'Lunch orders · 2026-11-20' })).toBeInTheDocument()
    expect(screen.getByText('Ordering open until 10:00')).toBeInTheDocument()
    const anna = screen.getByRole('row', { name: /Anna de Vries/ })
    expect(within(anna).getByText('Sandwich')).toBeInTheDocument()
    expect(within(anna).getByText('Tuna')).toBeInTheDocument()
    expect(within(anna).getByText('no onions')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Bram Jansen/ })).toBeInTheDocument()
    expect(screen.getByText('2 orders')).toBeInTheDocument()
  })

  it('removeAsksConfirmationThenCallsDelete', async () => {
    const user = userEvent.setup()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    render(<OrdersPage onUnauthorized={vi.fn()} />)
    const anna = await screen.findByRole('row', { name: /Anna de Vries/ })

    await user.click(within(anna).getByRole('button', { name: 'Remove' }))
    expect(confirm).toHaveBeenLastCalledWith("Remove Anna de Vries's order?")
    expect(screen.getByRole('row', { name: /Anna de Vries/ })).toBeInTheDocument()

    await user.click(within(anna).getByRole('button', { name: 'Remove' }))
    await vi.waitFor(() =>
      expect(screen.queryByRole('row', { name: /Anna de Vries/ })).not.toBeInTheDocument(),
    )
    expect(screen.getByText('1 order')).toBeInTheDocument()
  })

  it('showsClosedState', async () => {
    server.use(
      http.get('/api/admin/orders/today', () =>
        HttpResponse.json({ date: '2026-11-20', open: false, cutOff: '10:00', orders: [] }),
      ),
    )
    render(<OrdersPage onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Ordering closed')).toBeInTheDocument()
    expect(screen.queryByText(/Ordering open until/)).not.toBeInTheDocument()
    expect(screen.getByText('0 orders')).toBeInTheDocument()
  })

  it('draftEmailButtonOpensDraft', async () => {
    const user = userEvent.setup()
    render(<OrdersPage onUnauthorized={vi.fn()} />)

    await user.click(await screen.findByRole('button', { name: 'Draft email' }))

    expect(await screen.findByText('Order Lunch, Fri 20 Nov')).toBeInTheDocument()
  })

  it('callsOnUnauthorizedOn401', async () => {
    setMockLoggedIn(false)
    const onUnauthorized = vi.fn()
    render(<OrdersPage onUnauthorized={onUnauthorized} />)

    await vi.waitFor(() => expect(onUnauthorized).toHaveBeenCalled())
  })
})
