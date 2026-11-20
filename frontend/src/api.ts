// Typed client for the admin API described in contracts/openapi.yaml.

export interface OrderView {
  id: string
  displayName: string
  foodType: string
  item: string
  /** Empty string when there are none. */
  notes: string
  placedAt: string
}

export interface TodayOrders {
  date: string
  open: boolean
  cutOff: string
  orders: OrderView[]
}

export interface Item {
  id: number
  name: string
}

export interface FoodType {
  id: number
  name: string
  items: Item[]
}

export interface Settings {
  cutOff: string
  /** Empty string means "not set". */
  shopEmail: string
}

export interface EmailDraft {
  to: string
  subject: string
  body: string
  final: boolean
}

export type SaveResult = { ok: true } | { ok: false; error: string }
export type MutationResult<T> = { ok: true; value: T } | { ok: false; status: number; error: string }

/** Thrown when the session is missing or expired (HTTP 401). */
export class UnauthorizedError extends Error {
  constructor() {
    super('Not logged in')
    this.name = 'UnauthorizedError'
  }
}

/** Thrown for any other unexpected response. */
export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

const BASE = '/api/admin'
const UNSAFE = new Set(['POST', 'PUT', 'DELETE'])

function readCookie(name: string): string | null {
  for (const part of document.cookie.split(';')) {
    const [key, ...rest] = part.trim().split('=')
    if (key === name) return decodeURIComponent(rest.join('='))
  }
  return null
}

/** Sends a request. Unsafe methods carry the XSRF token, read fresh from the cookie each time. */
async function send(method: string, path: string, init: { body?: BodyInit; contentType?: string } = {}) {
  const headers = new Headers()
  if (init.contentType) headers.set('Content-Type', init.contentType)
  if (UNSAFE.has(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers.set('X-XSRF-TOKEN', token)
  }
  const url = new URL(BASE + path, window.location.href).toString()
  return fetch(url, { method, headers, body: init.body, credentials: 'same-origin' })
}

const sendJson = (method: string, path: string, body: unknown) =>
  send(method, path, { body: JSON.stringify(body), contentType: 'application/json' })

async function errorText(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { error?: unknown }
    if (typeof body.error === 'string') return body.error
  } catch {
    // No JSON body.
  }
  return `Request failed (${response.status})`
}

async function expectOk(response: Response): Promise<Response> {
  if (response.status === 401) throw new UnauthorizedError()
  if (!response.ok) throw new ApiError(response.status, await errorText(response))
  return response
}

async function getJson<T>(path: string): Promise<T> {
  const response = await expectOk(await send('GET', path))
  return (await response.json()) as T
}

/** For mutations whose 400/409 the screen shows to the manager. */
async function mutation<T>(response: Response, parse: boolean): Promise<MutationResult<T>> {
  if (response.status === 400 || response.status === 409) {
    return { ok: false, status: response.status, error: await errorText(response) }
  }
  await expectOk(response)
  return { ok: true, value: (parse ? await response.json() : undefined) as T }
}

// --- Session ---

export async function me(): Promise<string | null> {
  const response = await send('GET', '/me')
  if (response.status === 401) return null
  await expectOk(response)
  const body = (await response.json()) as { username: string }
  return body.username
}

export async function login(username: string, password: string): Promise<boolean> {
  // A fresh client has no XSRF cookie yet; GET /me makes the server set it.
  if (!readCookie('XSRF-TOKEN')) await me()
  const response = await send('POST', '/login', {
    body: new URLSearchParams({ username, password }).toString(),
    contentType: 'application/x-www-form-urlencoded',
  })
  if (response.status === 401) return false
  await expectOk(response)
  return true
}

export async function logout(): Promise<void> {
  await expectOk(await send('POST', '/logout'))
}

// --- Orders ---

export const todayOrders = () => getJson<TodayOrders>('/orders/today')

export async function deleteOrder(id: string): Promise<void> {
  await expectOk(await send('DELETE', `/orders/${encodeURIComponent(id)}`))
}

export const emailDraft = () => getJson<EmailDraft>('/email-draft')

// --- Menu ---

export const getMenu = () => getJson<FoodType[]>('/menu')

export async function createFoodType(name: string): Promise<MutationResult<FoodType>> {
  return mutation(await sendJson('POST', '/menu/food-types', { name }), true)
}

export async function renameFoodType(id: number, name: string): Promise<MutationResult<FoodType>> {
  return mutation(await sendJson('PUT', `/menu/food-types/${id}`, { name }), true)
}

export async function deleteFoodType(id: number): Promise<void> {
  await expectOk(await send('DELETE', `/menu/food-types/${id}`))
}

export async function addItem(foodTypeId: number, name: string): Promise<MutationResult<Item>> {
  return mutation(await sendJson('POST', `/menu/food-types/${foodTypeId}/items`, { name }), true)
}

export async function renameItem(id: number, name: string): Promise<MutationResult<Item>> {
  return mutation(await sendJson('PUT', `/menu/items/${id}`, { name }), true)
}

export async function deleteItem(id: number): Promise<void> {
  await expectOk(await send('DELETE', `/menu/items/${id}`))
}

// --- Settings ---

export const getSettings = () => getJson<Settings>('/settings')

export async function saveSettings(s: Settings): Promise<SaveResult> {
  const response = await sendJson('PUT', '/settings', s)
  if (response.status === 400) return { ok: false, error: await errorText(response) }
  await expectOk(response)
  return { ok: true }
}
