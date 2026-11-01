import api from './api'

export const driverService = {
  dashboard: () => api.get('/api/drivers/dashboard').then((r) => r.data),
  setOnline: (online) => api.put('/api/drivers/availability', { online }).then((r) => r.data),
  updateRickshaw: (body) => api.put('/api/drivers/rickshaw', body).then((r) => r.data),
  move: () => api.post('/api/drivers/move').then((r) => r.data),
  autoDrive: (enabled) => api.put('/api/drivers/auto-drive', { enabled }).then((r) => r.data),
  accept: (id) => api.post(`/api/rides/${id}/accept`).then((r) => r.data),
  reject: (id) => api.post(`/api/rides/${id}/reject`).then((r) => r.data),
  start: (id) => api.post(`/api/rides/${id}/start`).then((r) => r.data),
  complete: (id) => api.post(`/api/rides/${id}/complete`).then((r) => r.data),
  history: () => api.get('/api/drivers/rides').then((r) => r.data),
  profile: () => api.get('/api/drivers/profile').then((r) => r.data),
  updateProfile: (body) => api.put('/api/drivers/profile', body).then((r) => r.data),
}
