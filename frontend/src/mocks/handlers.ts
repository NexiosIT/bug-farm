import { http, HttpResponse } from 'msw'
import type { EmailDraft, FoodType, OrderView, Settings, TodayOrders } from '../api'

// Mock of contracts/openapi.yaml. Data follows the examples in the API description.

const BASE = '/api/admin'
const USERNAME = 'admin'
const PASSWORD = 'secret'
const XSRF_COOKIE = 'XSRF-TOKEN=mock-xsrf-token; Path=/'

interface MockState {
  loggedIn: boolean
  today: Omit<TodayOrders, 'orders'>
  orders: OrderView[]
  menu: FoodType[]
  settings: Settings
  nextId: number
}

function initialState(): MockState {
  return {
    loggedIn: false,
    today: { date: '2026-11-20', open: true, cutOff: '10:00' },
    orders: [
      {
        id: '3f2b8c1e-5a47-4d0e-9b1a-7c6d2e8f4a10',
        displayName: 'Anna de Vries',
        foodType: 'Sandwich',
        item: 'Tuna',
        notes: 'no onions',
        placedAt: '2026-11-20T08:15:30Z',
      },
      {
        id: '9a1d4e77-2c3b-4f58-8e60-1b5a0d7c3e92',
        displayName: 'Bram Jansen',
        foodType: 'Sandwich',
        item: 'Ham & cheese',
        notes: '',
        placedAt: '2026-11-20T08:42:05Z',
      },
    ],
    menu: [
      {
        id: 1,
        name: 'Sandwich',
        items: [
          { id: 11, name: 'Ham & cheese' },
          { id: 12, name: 'Tuna' },
        ],
      },
      { id: 2, name: 'Soup', items: [] },
    ],
    settings: { cutOff: '10:00', shopEmail: 'orders@sandwichshop.example' },
    nextId: 100,
  }
}

let state = initialState()

/** Restores the mock data to its starting point (used between tests). */
export function resetMockData(): void {
  state = initialState()
}

/** Starts the mock with a logged-in session (used by component tests). */
export function setMockLoggedIn(loggedIn: boolean): void {
  state.loggedIn = loggedIn
}

// The real server sets a readable XSRF-TOKEN cookie on its responses. Mock handlers run
// in the page (browser) or the test process (jsdom), so set it on document.cookie directly:
// msw does not apply mocked Set-Cookie headers to document.cookie under Node.
function issueXsrfCookie() {
  if (typeof document !== 'undefined') document.cookie = XSRF_COOKIE
}
const json = (body: unknown, status = 200) => {
  issueXsrfCookie()
  return HttpResponse.json(body as Record<string, unknown>, { status })
}
const empty = (status: number) => {
  issueXsrfCookie()
  return new HttpResponse(null, { status })
}
const error = (status: number, message: string) => json({ error: message }, status)

/** Unsafe methods need the X-XSRF-TOKEN header (403 otherwise). */
const missingXsrf = (request: Request) => !request.headers.get('X-XSRF-TOKEN')

const sameName = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()

function checkName(name: unknown, max: number): string | null {
  if (typeof name !== 'string' || name.trim() === '') return 'Name must not be blank'
  if (name.trim().length > max) return `Name must be at most ${max} characters`
  return null
}

function findItem(id: number) {
  for (const foodType of state.menu) {
    const item = foodType.items.find((i) => i.id === id)
    if (item) return { foodType, item }
  }
  return null
}

function buildDraft(): EmailDraft {
  const counts = new Map<string, number>()
  for (const o of state.orders) counts.set(o.item, (counts.get(o.item) ?? 0) + 1)
  const totals = [...counts].map(([item, n]) => `${n} × ${item}`).join('\n')
  const perPerson = state.orders
    .map((o) => `${o.displayName}: ${o.item}${o.notes ? ` (${o.notes})` : ''}`)
    .join('\n')
  return {
    to: state.settings.shopEmail,
    subject: 'Order Lunch, Fri 20 Nov',
    body: `Order for Fri 20 Nov\n\n${totals}\n\nPer person:\n${perPerson}`,
    final: !state.today.open,
  }
}

export const handlers = [
  http.post(`${BASE}/login`, async ({ request }) => {
    if (missingXsrf(request)) return empty(403)
    const form = new URLSearchParams(await request.text())
    if (form.get('username') !== USERNAME || form.get('password') !== PASSWORD) return empty(401)
    state.loggedIn = true
    return empty(200)
  }),

  http.post(`${BASE}/logout`, ({ request }) => {
    if (missingXsrf(request)) return empty(403)
    state.loggedIn = false
    return empty(200)
  }),

  http.get(`${BASE}/me`, () => (state.loggedIn ? json({ username: USERNAME }) : empty(401))),

  http.get(`${BASE}/orders/today`, () => {
    if (!state.loggedIn) return empty(401)
    const orders = [...state.orders].sort((a, b) => a.displayName.localeCompare(b.displayName))
    return json({ ...state.today, orders })
  }),

  http.delete(`${BASE}/orders/:id`, ({ request, params }) => {
    if (!state.loggedIn) return empty(401)
    if (missingXsrf(request)) return empty(403)
    const before = state.orders.length
    state.orders = state.orders.filter((o) => o.id !== params.id)
    return empty(state.orders.length < before ? 204 : 404)
  }),

  http.get(`${BASE}/menu`, () => (state.loggedIn ? json(state.menu) : empty(401))),

  http.post(`${BASE}/menu/food-types`, async ({ request }) => {
    if (!state.loggedIn) return empty(401)
    if (missingXsrf(request)) return empty(403)
    const { name } = (await request.json()) as { name: unknown }
    const invalid = checkName(name, 100)
    if (invalid) return error(400, invalid)
    if (state.menu.some((f) => sameName(f.name, name as string))) return error(409, 'Name already exists')
    const created: FoodType = { id: state.nextId++, name: (name as string).trim(), items: [] }
    state.menu.push(created)
    return json(created, 201)
  }),

  http.put(`${BASE}/menu/food-types/:id`, async ({ request, params }) => {
    if (!state.loggedIn) return empty(401)
    if (missingXsrf(request)) return empty(403)
    const foodType = state.menu.find((f) => f.id === Number(params.id))
    if (!foodType) return empty(404)
    const { name } = (await request.json()) as { name: unknown }
    const invalid = checkName(name, 100)
    if (invalid) return error(400, invalid)
    if (state.menu.some((f) => f !== foodType && sameName(f.name, name as string))) {
      return error(409, 'Name already exists')
    }
    foodType.name = (name as string).trim()
    return json(foodType)
  }),

  http.delete(`${BASE}/menu/food-types/:id`, ({ request, params }) => {
    if (!state.loggedIn) return empty(401)
    if (missingXsrf(request)) return empty(403)
    const before = state.menu.length
    state.menu = state.menu.filter((f) => f.id !== Number(params.id))
    return empty(state.menu.length < before ? 204 : 404)
  }),

  http.post(`${BASE}/menu/food-types/:id/items`, async ({ request, params }) => {
    if (!state.loggedIn) return empty(401)
    if (missingXsrf(request)) return empty(403)
    const foodType = state.menu.find((f) => f.id === Number(params.id))
    if (!foodType) return empty(404)
    const { name } = (await request.json()) as { name: unknown }
    const invalid = checkName(name, 200)
    if (invalid) return error(400, invalid)
    if (foodType.items.some((i) => sameName(i.name, name as string))) return error(409, 'Name already exists')
    const created = { id: state.nextId++, name: (name as string).trim() }
    foodType.items.push(created)
    return json(created, 201)
  }),

  http.put(`${BASE}/menu/items/:id`, async ({ request, params }) => {
    if (!state.loggedIn) return empty(401)
    if (missingXsrf(request)) return empty(403)
    const found = findItem(Number(params.id))
    if (!found) return empty(404)
    const { name } = (await request.json()) as { name: unknown }
    const invalid = checkName(name, 200)
    if (invalid) return error(400, invalid)
    if (found.foodType.items.some((i) => i !== found.item && sameName(i.name, name as string))) {
      return error(409, 'Name already exists')
    }
    found.item.name = (name as string).trim()
    return json(found.item)
  }),

  http.delete(`${BASE}/menu/items/:id`, ({ request, params }) => {
    if (!state.loggedIn) return empty(401)
    if (missingXsrf(request)) return empty(403)
    const found = findItem(Number(params.id))
    if (!found) return empty(404)
    found.foodType.items = found.foodType.items.filter((i) => i !== found.item)
    return empty(204)
  }),

  http.get(`${BASE}/settings`, () => (state.loggedIn ? json(state.settings) : empty(401))),

  http.put(`${BASE}/settings`, async ({ request }) => {
    if (!state.loggedIn) return empty(401)
    if (missingXsrf(request)) return empty(403)
    const body = (await request.json()) as Partial<Settings>
    if (typeof body.cutOff !== 'string' || !/^([01]\d|2[0-3]):[0-5]\d$/.test(body.cutOff)) {
      return error(400, 'cutOff: must be HH:mm')
    }
    if (typeof body.shopEmail !== 'string' || (body.shopEmail !== '' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.shopEmail))) {
      return error(400, 'shopEmail: must be a valid e-mail address')
    }
    state.settings = { cutOff: body.cutOff, shopEmail: body.shopEmail }
    state.today.cutOff = body.cutOff
    return json(state.settings)
  }),

  http.get(`${BASE}/email-draft`, () => (state.loggedIn ? json(buildDraft()) : empty(401))),
]
