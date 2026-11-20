import { useState } from 'react'
import { deleteOrder, todayOrders, type OrderView } from './api'
import { EmailDraftView } from './EmailDraftView'
import { useApiData } from './useApi'

interface Props {
  onUnauthorized: () => void
}

export function OrdersPage({ onUnauthorized }: Props) {
  const { data, error, reload, run } = useApiData(todayOrders, onUnauthorized)
  // Kept apart from the load error, which a successful reload clears.
  const [actionError, setActionError] = useState<string | null>(null)
  // Bumping the version remounts EmailDraftView, so the draft is fetched again.
  const [draftVersion, setDraftVersion] = useState(0)

  async function remove(order: OrderView) {
    if (!window.confirm(`Remove ${order.displayName}'s order?`)) return
    setActionError(null)
    await run(
      () => deleteOrder(order.id),
      (message) => setActionError(`Could not remove ${order.displayName}'s order: ${message}`),
    )
    // Reload either way: after a 404 the order was already gone.
    await reload()
    setDraftVersion((v) => (v === 0 ? 0 : v + 1))
  }

  if (!data) return error ? <p role="alert">{error}</p> : <p>Loading orders…</p>

  const count = data.orders.length
  return (
    <div>
      <h1>Lunch orders · {data.date}</h1>
      <p>{data.open ? `Ordering open until ${data.cutOff}` : 'Ordering closed'}</p>
      {error && <p role="alert">{error}</p>}
      {actionError && <p role="alert">{actionError}</p>}
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Food type</th>
            <th>Item</th>
            <th>Notes</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {data.orders.map((order) => (
            <tr key={order.id}>
              <td>{order.displayName}</td>
              <td>{order.foodType}</td>
              <td>{order.item}</td>
              <td>{order.notes}</td>
              <td>
                <button type="button" onClick={() => void remove(order)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        {count} {count === 1 ? 'order' : 'orders'}
      </p>
      {/* Clicking again fetches a fresh draft (e.g. after the cut-off has passed). */}
      <button type="button" onClick={() => setDraftVersion((v) => v + 1)}>
        Draft email
      </button>
      {draftVersion > 0 && <EmailDraftView key={draftVersion} onUnauthorized={onUnauthorized} />}
    </div>
  )
}
