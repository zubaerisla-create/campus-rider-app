import api from './api'

export const rideService = {
  available: (pickup, destination, seats, sort) =>
    api.get('/api/rides/available', { params: { pickup, destination, seats, sort } }).then((r) => r.data),
  request: (body) => api.post('/api/rides/request', body).then((r) => r.data),
  get: (id) => api.get(`/api/rides/${id}`).then((r) => r.data),
  cancel: (id) => api.post(`/api/rides/${id}/cancel`).then((r) => r.data),
  current: () => api.get('/api/passengers/rides/current').then((r) => (r.status === 204 ? null : r.data)),
  history: () => api.get('/api/passengers/rides').then((r) => r.data),
  profile: () => api.get('/api/passengers/profile').then((r) => r.data),
  updateProfile: (body) => api.put('/api/passengers/profile', body).then((r) => r.data),
}
