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

  if (!data) return error ? <p role="alert">{error}</p> : <p className="quiet">Loading orders…</p>

  const count = data.orders.length
  return (
    <div>
      <div className="page-head">
        <div>
          <p className="eyebrow">— Today</p>
          <h1>Lunch orders · {data.date}</h1>
          <p className={data.open ? 'status status-open' : 'status'}>
            {data.open ? `Ordering open until ${data.cutOff}` : 'Ordering closed'}
          </p>
        </div>
        <p className="stat">{`${count} ${count === 1 ? 'order' : 'orders'}`}</p>
      </div>
      {error && <p role="alert">{error}</p>}
      {actionError && <p role="alert">{actionError}</p>}
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Food type</th>
            <th>Item</th>
            <th>Notes</th>
            <th>
              <span className="visually-hidden">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {data.orders.map((order) => (
            <tr key={order.id}>
              <td className="name-cell">{order.displayName}</td>
              <td>{order.foodType}</td>
              <td>{order.item}</td>
              <td className={order.notes ? undefined : 'empty-cell'}>{order.notes || '—'}</td>
              <td>
                <button type="button" className="btn btn-quiet btn-danger" onClick={() => void remove(order)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
          {count === 0 && (
            <tr>
              <td colSpan={5} className="empty-cell">
                No orders yet today.
              </td>
            </tr>
          )}
        </tbody>
      </table>
      <div className="table-foot">
        <p className="quiet">Removing an order also updates the draft email.</p>
        {/* Clicking again fetches a fresh draft (e.g. after the cut-off has passed). */}
        <button type="button" className="btn btn-primary" onClick={() => setDraftVersion((v) => v + 1)}>
          Draft email
        </button>
      </div>
      {draftVersion > 0 && <EmailDraftView key={draftVersion} onUnauthorized={onUnauthorized} />}
    </div>
  )
}
