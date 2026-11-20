import { useCallback, useEffect, useState } from 'react'
import { UnauthorizedError } from './api'

/**
 * Runs an API call and reports a 401 to `onUnauthorized` (session expired).
 * Other failures become a readable error message.
 */
export function useApiCall(onUnauthorized: () => void) {
  return useCallback(
    async <T,>(call: () => Promise<T>, onError: (message: string) => void): Promise<T | undefined> => {
      try {
        return await call()
      } catch (e) {
        if (e instanceof UnauthorizedError) onUnauthorized()
        else onError(e instanceof Error ? e.message : 'Something went wrong.')
        return undefined
      }
    },
    [onUnauthorized],
  )
}

/** Loads data on mount; `reload` fetches it again. */
export function useApiData<T>(load: () => Promise<T>, onUnauthorized: () => void) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const run = useApiCall(onUnauthorized)

  const reload = useCallback(async () => {
    const result = await run(load, setError)
    if (result !== undefined) {
      setData(result)
      setError(null)
    }
  }, [run, load])

  useEffect(() => {
    void reload()
  }, [reload])

  return { data, setData, error, setError, reload, run }
}
