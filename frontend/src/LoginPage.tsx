import { useState, type FormEvent } from 'react'
import { login } from './api'
import wordmark from './assets/nexios-wordmark-white.webp'

interface Props {
  onLoggedIn: () => void
}

export function LoginPage({ onLoggedIn }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (await login(username, password)) {
        onLoggedIn()
      } else {
        setError('Wrong username or password.')
      }
    } catch {
      setError('Could not log in. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login">
      <aside className="login-side">
        <img className="wordmark" src={wordmark} alt="Nexios" />
        <div>
          <p className="eyebrow">— Lunch admin</p>
          <p className="login-title">Today's lunch orders, in one email to the shop.</p>
        </div>
        <dl className="login-facts">
          <div>
            <dt>Orders from</dt>
            <dd>
              <strong>Sandwishes in Teams</strong>
            </dd>
          </div>
          <div>
            <dt>Ordering closes</dt>
            <dd>
              <strong>At the cut-off</strong>
            </dd>
          </div>
          <div>
            <dt>You send</dt>
            <dd>
              <strong>The draft email</strong>
            </dd>
          </div>
        </dl>
      </aside>
      <div className="login-main">
        <form className="login-form" onSubmit={submit}>
          <div>
            <p className="eyebrow">— Order manager</p>
            <h1>Log in</h1>
          </div>
          <label>
            Username
            <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          {error && <p role="alert">{error}</p>}
          <button type="submit" className="btn btn-primary" disabled={busy}>
            Log in
          </button>
        </form>
      </div>
    </main>
  )
}
