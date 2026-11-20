import { useState, type FormEvent } from 'react'
import { login } from './api'

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
      <h1>Lunch admin</h1>
      <form onSubmit={submit}>
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
        <button type="submit" disabled={busy}>
          Log in
        </button>
      </form>
    </main>
  )
}
