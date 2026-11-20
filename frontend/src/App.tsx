import { useCallback, useEffect, useState } from 'react'
import { logout, me } from './api'
import { LoginPage } from './LoginPage'
import { MenuPage } from './MenuPage'
import { OrdersPage } from './OrdersPage'
import { SettingsPage } from './SettingsPage'

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

  async function doLogout() {
    try {
      await logout()
    } finally {
      setSession({ state: 'loggedOut' })
    }
  }

  if (session.state === 'loading') return <p>Loading…</p>
  if (session.state === 'loggedOut') return <LoginPage onLoggedIn={() => void refreshSession()} />

  return (
    <div className="app">
      <header>
        <nav role="tablist">
          {TABS.map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
        <span className="user">
          {session.username} <button onClick={() => void doLogout()}>Log out</button>
        </span>
      </header>
      <section role="tabpanel">{renderTab(tab, onUnauthorized)}</section>
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
