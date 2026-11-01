import axios from 'axios'

/**
 * Axios client for the Spring Boot API.
 * - Same-origin requests (the Vite dev server proxies /api), so the HttpOnly session
 *   cookie is sent automatically.
 * - Axios copies the XSRF-TOKEN cookie into the X-XSRF-TOKEN header (Spring Security CSRF).
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
  timeout: 15000,
})

let unauthorizedHandler = null
export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401 && !error.config?.url?.startsWith('/api/auth/')) {
      unauthorizedHandler?.()
    }
    return Promise.reject(error)
  },
)

/** Human-readable message from an API error. */
export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) return fallback
  if (error.response?.data?.message) return error.response.data.message
  if (error.code === 'ECONNABORTED') return 'The server took too long to respond.'
  if (!error.response) return 'Cannot reach the Campus Rider server. Is the backend running?'
  return fallback
}

export function fieldErrors(error) {
  return error?.response?.data?.fieldErrors || {}
}

export default api
