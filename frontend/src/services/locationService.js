import api from './api'

let mapPromise = null

export const locationService = {
  /** The campus graph (locations + roads). Cached for the session: it never changes at runtime. */
  map: () => {
    if (!mapPromise) {
      mapPromise = api.get('/api/map').then((r) => r.data).catch((e) => {
        mapPromise = null
        throw e
      })
    }
    return mapPromise
  },
  /** Trie prefix search on the backend. */
  search: (q) => api.get('/api/locations/search', { params: { q, limit: 8 } }).then((r) => r.data),
  route: (from, to) => api.get('/api/routes/shortest-path', { params: { from, to } }).then((r) => r.data),
  positions: () => api.get('/api/tracking/rickshaws').then((r) => r.data),
}
