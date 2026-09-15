const API_BASE = '/api'

export function getToken() {
  return localStorage.getItem('livehouse_token') || ''
}

export function setToken(token) {
  if (token) {
    localStorage.setItem('livehouse_token', token)
  } else {
    localStorage.removeItem('livehouse_token')
  }
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  const token = getToken()

  if (token) {
    headers.set('authorization', token)
  }

  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    body: options.body && !(options.body instanceof FormData)
      ? JSON.stringify(options.body)
      : options.body
  })

  if (response.status === 401) {
    setToken('')
    throw new Error('登录已过期，请重新登录')
  }

  const result = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(result?.errorMsg || `请求失败：${response.status}`)
  }

  if (result && result.success === false) {
    throw new Error(result.errorMsg || '操作失败')
  }

  return result?.data ?? result
}

export const api = {
  sendCode(phone) {
    return request(`/user/code?phone=${encodeURIComponent(phone)}`, { method: 'POST' })
  },
  login(payload) {
    return request('/user/login', { method: 'POST', body: payload })
  },
  me() {
    return request('/user/me')
  },
  profile() {
    return request('/user/profile')
  },
  updateProfile(payload) {
    return request('/user/profile', { method: 'PUT', body: payload })
  },
  hotShows(current = 1) {
    return request(`/show/hot?current=${current}`)
  },
  searchShows(name, current = 1) {
    return request(`/show/of/name?name=${encodeURIComponent(name || '')}&current=${current}`)
  },
  showsByCity(city, current = 1, coords = {}) {
    const params = new URLSearchParams({ current })
    if (city) params.set('city', city)
    if (coords.x && coords.y) {
      params.set('x', coords.x)
      params.set('y', coords.y)
    }
    return request(`/show/of/city?${params.toString()}`)
  },
  showDetail(id) {
    return request(`/show/${id}`)
  },
  venueDetail(id) {
    return request(`/venue/${id}`)
  },
  venuesByCity(city) {
    return request(`/venue/of/city?city=${encodeURIComponent(city)}`)
  },
  ticketTypes(showId) {
    return request(`/ticket-type/of/show/${showId}`)
  },
  seckill(ticketTypeId, quantity) {
    return request(`/seckill/ticket/${ticketTypeId}?quantity=${quantity}`, { method: 'POST' })
  },
  orders() {
    return request('/order/list')
  },
  orderDetail(orderId) {
    return request(`/order/${orderId}`)
  },
  payOrder(orderId) {
    return request(`/order/pay/${orderId}`, { method: 'POST' })
  },
  simulatePayment(orderId) {
    return request(`/ticket/simulate-payment/${orderId}`, { method: 'POST' })
  },
  ticketDetail(ticketCode) {
    return request(`/ticket/detail/${encodeURIComponent(ticketCode)}`)
  },
  verifyTicket(ticketCode) {
    return request(`/ticket/verify/${encodeURIComponent(ticketCode)}`, { method: 'POST' })
  },
  myTickets() {
    return request('/ticket/my')
  },
  uploadImage(file, folder = '') {
    const form = new FormData()
    form.append('file', file)
    if (folder) form.append('folder', folder)
    return request('/upload/image', { method: 'POST', body: form })
  },
  redisStock(ticketTypeId) {
    return request(`/admin/stock/${ticketTypeId}`)
  },
  verifyStats(showId) {
    return request(`/admin/verify-stats/${showId}`)
  },
  rateLimit(ip) {
    return request(`/admin/rate-limit/${encodeURIComponent(ip)}`)
  }
}
