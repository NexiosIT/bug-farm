import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

async function enableMocks() {
  // VITE_MOCK=1 serves the API mock (src/mocks/handlers.ts) instead of the real backend.
  if (import.meta.env.DEV && import.meta.env.VITE_MOCK === '1') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledFrame: 'bypass' })
  }
}

void enableMocks().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
