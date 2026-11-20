import { useState } from 'react'
import { deleteOrder, todayOrders, type OrderView } from './api'
import { EmailDraftView } from './EmailDraftView'
import { useApiData } from './useApi'

interface Props {
  onUnauthorized: () => void
}

export function OrdersPage({ onUnauthorized }: Props) {
  const { data, error, setError, reload, run } = useApiData(todayOrders, onUnauthorized)
  const [showDraft, setShowDraft] = useState(false)

  async function remove(order: OrderView) {
    if (!window.confirm(`Remove ${order.displayName}'s order?`)) return
    await run(() => deleteOrder(order.id), setError)
    // Reload either way: after a 404 the order was already gone.
    await reload()
  }

  if (!data) return error ? <p role="alert">{error}</p> : <p>Loading orders…</p>

  const count = data.orders.length
  return (
    <div>
      <h1>Lunch orders · {data.date}</h1>
      <p>{data.open ? `Ordering open until ${data.cutOff}` : 'Ordering closed'}</p>
      {error && <p role="alert">{error}</p>}
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
      <button type="button" onClick={() => setShowDraft(true)} disabled={showDraft}>
        Draft email
      </button>
      {showDraft && <EmailDraftView onUnauthorized={onUnauthorized} />}
    </div>
  )
}
