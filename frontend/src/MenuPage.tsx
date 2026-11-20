import { useState, type FormEvent } from 'react'
import {
  addItem,
  createFoodType,
  deleteFoodType,
  deleteItem,
  getMenu,
  renameFoodType,
  renameItem,
  type FoodType,
  type Item,
  type MutationResult,
} from './api'
import { useApiData } from './useApi'

interface Props {
  onUnauthorized: () => void
}

/** null on success, otherwise the message to show. */
type Outcome = string | null

function messageFor<T>(result: MutationResult<T>): Outcome {
  if (result.ok) return null
  return result.status === 409 ? 'That name already exists.' : result.error
}

export function MenuPage({ onUnauthorized }: Props) {
  const { data: menu, error, reload, run } = useApiData(getMenu, onUnauthorized)

  /** Runs a mutation, reloads the menu, and returns the message to show (if any). */
  async function mutate(call: () => Promise<Outcome | void>): Promise<Outcome> {
    let message: Outcome = null
    const outcome = await run(call, (m) => (message = m))
    await reload()
    return message ?? (outcome || null)
  }

  if (!menu) return error ? <p role="alert">{error}</p> : <p>Loading menu…</p>

  return (
    <div>
      <h1>Menu</h1>
      {error && <p role="alert">{error}</p>}
      {menu.map((foodType) => (
        <FoodTypeSection key={foodType.id} foodType={foodType} mutate={mutate} />
      ))}
      <AddForm
        label="New food type"
        button="Add food type"
        onAdd={(name) => mutate(async () => messageFor(await createFoodType(name)))}
      />
    </div>
  )
}

type Mutate = (call: () => Promise<Outcome | void>) => Promise<Outcome>

function FoodTypeSection({ foodType, mutate }: { foodType: FoodType; mutate: Mutate }) {
  const [message, setMessage] = useState<Outcome>(null)
  const [renaming, setRenaming] = useState(false)

  async function act(call: () => Promise<Outcome | void>): Promise<boolean> {
    const result = await mutate(call)
    setMessage(result)
    return result === null
  }

  async function remove() {
    if (!window.confirm(`Remove ${foodType.name} and all its items?`)) return
    await act(() => deleteFoodType(foodType.id))
  }

  return (
    <section aria-label={foodType.name} className="food-type">
      {renaming ? (
        <RenameForm
          current={foodType.name}
          onCancel={() => setRenaming(false)}
          onSave={async (name) => {
            if (await act(async () => messageFor(await renameFoodType(foodType.id, name)))) setRenaming(false)
          }}
        />
      ) : (
        <h2>
          {foodType.name}{' '}
          <button type="button" aria-label={`Rename ${foodType.name}`} onClick={() => setRenaming(true)}>
            Rename
          </button>{' '}
          <button type="button" aria-label={`Remove ${foodType.name}`} onClick={() => void remove()}>
            Remove
          </button>
        </h2>
      )}
      <ul>
        {foodType.items.map((item) => (
          <ItemRow key={item.id} item={item} act={act} />
        ))}
      </ul>
      <AddForm
        label={`New item for ${foodType.name}`}
        button="Add item"
        onAdd={(name) => mutate(async () => messageFor(await addItem(foodType.id, name)))}
      />
      {message && <p role="alert">{message}</p>}
    </section>
  )
}

function ItemRow({ item, act }: { item: Item; act: (call: () => Promise<Outcome | void>) => Promise<boolean> }) {
  const [renaming, setRenaming] = useState(false)

  if (renaming) {
    return (
      <li>
        <RenameForm
          current={item.name}
          onCancel={() => setRenaming(false)}
          onSave={async (name) => {
            if (await act(async () => messageFor(await renameItem(item.id, name)))) setRenaming(false)
          }}
        />
      </li>
    )
  }

  return (
    <li>
      <span>{item.name}</span>{' '}
      <button type="button" aria-label={`Rename ${item.name}`} onClick={() => setRenaming(true)}>
        Rename
      </button>{' '}
      <button type="button" aria-label={`Remove ${item.name}`} onClick={() => void act(() => deleteItem(item.id))}>
        Remove
      </button>
    </li>
  )
}

/** A single-field add form. `onAdd` returns null on success (the field is cleared), or a message to show. */
function AddForm({ label, button, onAdd }: { label: string; button: string; onAdd: (name: string) => Promise<Outcome> }) {
  const [name, setName] = useState('')
  const [message, setMessage] = useState<Outcome>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    const result = await onAdd(name)
    setBusy(false)
    setMessage(result)
    if (result === null) setName('')
  }

  return (
    <form onSubmit={submit} className="add-form">
      <label>
        {label}
        <input value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <button type="submit" disabled={busy}>
        {button}
      </button>
      {message && <p role="alert">{message}</p>}
    </form>
  )
}

function RenameForm({
  current,
  onSave,
  onCancel,
}: {
  current: string
  onSave: (name: string) => Promise<void>
  onCancel: () => void
}) {
  const [name, setName] = useState(current)

  async function submit(event: FormEvent) {
    event.preventDefault()
    await onSave(name)
  }

  return (
    <form onSubmit={submit} className="rename-form">
      <label>
        New name for {current}
        <input value={name} onChange={(e) => setName(e.target.value)} autoFocus />
      </label>
      <button type="submit">Save</button>
      <button type="button" onClick={onCancel}>
        Cancel
      </button>
    </form>
  )
}
