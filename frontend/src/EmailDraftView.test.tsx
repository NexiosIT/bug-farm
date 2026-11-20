import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { setMockLoggedIn } from './mocks/handlers'
import { EmailDraftView } from './EmailDraftView'

const draft = {
  to: 'orders@sandwichshop.example',
  subject: 'Order Lunch, Fri 20 Nov',
  body: 'Order for Fri 20 Nov\n\n1 × Tuna\n\nPer person:\nAnna de Vries: Tuna',
  final: false,
}

describe('EmailDraftView', () => {
  beforeEach(() => {
    setMockLoggedIn(true)
    server.use(http.get('/api/admin/email-draft', () => HttpResponse.json(draft)))
  })
  afterEach(() => vi.useRealTimers())

  it('copyBodyWritesBodyToClipboard', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')
    render(<EmailDraftView onUnauthorized={vi.fn()} />)

    const copyBody = await screen.findByRole('button', { name: 'Copy Body' })
    await user.click(copyBody)

    expect(writeText).toHaveBeenCalledWith(draft.body)
    expect(copyBody).toHaveTextContent('Copied')
    act(() => vi.advanceTimersByTime(2000))
    expect(copyBody).toHaveTextContent('Copy')
    expect(copyBody).not.toHaveTextContent('Copied')
  })

  it('showsStillOpenNoteWhenNotFinal', async () => {
    render(<EmailDraftView onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Ordering is still open — orders may still change.')).toBeInTheDocument()
    expect(screen.getByText(draft.to)).toBeInTheDocument()
    expect(screen.getByText(draft.subject)).toBeInTheDocument()
  })

  it('hidesNoteWhenFinal', async () => {
    server.use(http.get('/api/admin/email-draft', () => HttpResponse.json({ ...draft, final: true })))
    render(<EmailDraftView onUnauthorized={vi.fn()} />)

    expect(await screen.findByText(draft.subject)).toBeInTheDocument()
    expect(screen.queryByText(/Ordering is still open/)).not.toBeInTheDocument()
  })
})
