import { useCallback, useEffect, useState } from 'react'
import { logout, me, UnauthorizedError } from './api'
import { LoginPage } from './LoginPage'
import { MenuPage } from './MenuPage'
import { OrdersPage } from './OrdersPage'
import { SettingsPage } from './SettingsPage'
import wordmark from './assets/nexios-wordmark-white.webp'

type Tab = 'orders' | 'menu' | 'settings'

const TABS: { id: Tab; label: string }[] = [
  { id: 'orders', label: "Today's orders" },
  { id: 'menu', label: 'Menu' },
  { id: 'settings', label: 'Settings' },
]

type Session = { state: 'loading' } | { state: 'loggedOut' } | { state: 'loggedIn'; username: string }

export default function App() {
  const [session, setSession] = useState<Session>({ state: 'loading' })
  const [tab, setTab] = useState<Tab>('orders')

  const refreshSession = useCallback(async () => {
    try {
      const username = await me()
      setSession(username ? { state: 'loggedIn', username } : { state: 'loggedOut' })
    } catch {
      setSession({ state: 'loggedOut' })
    }
  }, [])

  useEffect(() => {
    void refreshSession()
  }, [refreshSession])

  /** Called by pages when the server answers 401 (session expired). */
  const onUnauthorized = useCallback(() => setSession({ state: 'loggedOut' }), [])

  const [logoutError, setLogoutError] = useState<string | null>(null)

  async function doLogout() {
    setLogoutError(null)
    try {
      await logout()
      setSession({ state: 'loggedOut' })
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        setSession({ state: 'loggedOut' })
      } else {
        // The server session may still be alive: don't pretend the user is logged out.
        setLogoutError('Could not log out. Please try again.')
      }
    }
  }

  if (session.state === 'loading') return <p className="page quiet">Loading…</p>
  if (session.state === 'loggedOut') return <LoginPage onLoggedIn={() => void refreshSession()} />

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <div className="brand">
            <img className="wordmark" src={wordmark} alt="Nexios" />
            <span className="brand-product">Lunch admin</span>
          </div>
          <nav role="tablist" aria-label="Sections">
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                aria-selected={tab === t.id}
                onClick={() => setTab(t.id)}
              >
                {t.label}
              </button>
            ))}
          </nav>
          <span className="user">
            {session.username}{' '}
            <button type="button" className="btn btn-quiet" onClick={() => void doLogout()}>
              Log out
            </button>
          </span>
        </div>
      </header>
      <main className="page">
        {logoutError && <p role="alert">{logoutError}</p>}
        <section role="tabpanel" className="panel">
          {renderTab(tab, onUnauthorized)}
        </section>
      </main>
    </div>
  )
}

function renderTab(tab: Tab, onUnauthorized: () => void) {
  switch (tab) {
    case 'orders':
      return <OrdersPage onUnauthorized={onUnauthorized} />
    case 'menu':
      return <MenuPage onUnauthorized={onUnauthorized} />
    case 'settings':
      return <SettingsPage onUnauthorized={onUnauthorized} />
  }
}
