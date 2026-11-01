import api from './api'

export const authService = {
  csrf: () => api.get('/api/auth/csrf'),
  me: () => api.get('/api/auth/me').then((r) => (r.status === 204 ? null : r.data)),
  login: (role, username, password) =>
    api.post('/api/auth/login', { role, username, password }).then((r) => r.data),
  registerPassenger: (body) => api.post('/api/auth/passenger/register', body).then((r) => r.data),
  registerDriver: (body) => api.post('/api/auth/driver/register', body).then((r) => r.data),
  logout: () => api.post('/api/auth/logout'),
}
