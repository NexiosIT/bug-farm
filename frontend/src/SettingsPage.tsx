import { useEffect, useState, type FormEvent } from 'react'
import { getSettings, saveSettings } from './api'
import { useApiData } from './useApi'

interface Props {
  onUnauthorized: () => void
}

export function SettingsPage({ onUnauthorized }: Props) {
  const { data: settings, error, setError, run } = useApiData(getSettings, onUnauthorized)
  const [cutOff, setCutOff] = useState('')
  const [shopEmail, setShopEmail] = useState('')
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (settings) {
      setCutOff(settings.cutOff)
      setShopEmail(settings.shopEmail)
    }
  }, [settings])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setSaved(false)
    setError(null)
    // The server validates; its 400 message is shown as-is.
    const result = await run(() => saveSettings({ cutOff: cutOff.trim(), shopEmail: shopEmail.trim() }), setError)
    setBusy(false)
    if (!result) return
    if (result.ok) setSaved(true)
    else setError(result.error)
  }

  if (!settings && !error) return <p>Loading settings…</p>

  return (
    <div>
      <h1>Settings</h1>
      <form className="stack" onSubmit={submit} noValidate>
        <label>
          Cut-off time
          <input
            value={cutOff}
            onChange={(e) => setCutOff(e.target.value)}
            placeholder="HH:mm"
            inputMode="numeric"
          />
        </label>
        <label>
          Shop email address
          <input type="email" value={shopEmail} onChange={(e) => setShopEmail(e.target.value)} />
        </label>
        <div>
          <button type="submit" disabled={busy || !settings}>
            Save
          </button>{' '}
          {saved && <span role="status">Saved.</span>}
        </div>
        {error && <p role="alert">{error}</p>}
      </form>
    </div>
  )
}
