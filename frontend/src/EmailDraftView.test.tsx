import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { setMockLoggedIn } from './mocks/handlers'
import { EmailDraftView, mailtoHref } from './EmailDraftView'

const draft = {
  to: 'orders@sandwichshop.example',
  subject: 'Order Lunch, Fri 20 Nov',
  body: 'Order for Fri 20 Nov\n\n1 × Tuna\n\nPer person:\nAnna de Vries: Tuna',
  final: false,
}

describe('mailtoHref', () => {
  it('encodesSubjectAndBodyWithCrlfLineBreaks', () => {
    const href = mailtoHref({
      to: 'orders@sandwichshop.example',
      subject: 'Order Lunch, Fri 20 Nov',
      body: '1 × Ham & cheese\nAnna: Tuna?',
      final: true,
    })
    expect(href).toBe(
      'mailto:orders@sandwichshop.example' +
        '?subject=Order%20Lunch%2C%20Fri%2020%20Nov' +
        '&body=1%20%C3%97%20Ham%20%26%20cheese%0D%0AAnna%3A%20Tuna%3F',
    )
  })
})

describe('EmailDraftView', () => {
  beforeEach(() => {
    setMockLoggedIn(true)
    server.use(http.get('/api/admin/email-draft', () => HttpResponse.json(draft)))
  })

  it('opensOutlookLinkWithDraft', async () => {
    render(<EmailDraftView onUnauthorized={vi.fn()} />)

    const link = await screen.findByRole('link', { name: 'Open in Outlook' })
    expect(link).toHaveAttribute('href', mailtoHref(draft))
    expect(screen.queryByRole('button', { name: /^Copy/ })).not.toBeInTheDocument()
  })

  it('disablesButtonWhenNoShopEmail', async () => {
    server.use(http.get('/api/admin/email-draft', () => HttpResponse.json({ ...draft, to: '  ' })))
    render(<EmailDraftView onUnauthorized={vi.fn()} />)

    expect(await screen.findByRole('button', { name: 'Open in Outlook' })).toBeDisabled()
    expect(screen.queryByRole('link', { name: 'Open in Outlook' })).not.toBeInTheDocument()
    expect(screen.getByText('No shop email set. Add it under Settings.')).toBeInTheDocument()
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
