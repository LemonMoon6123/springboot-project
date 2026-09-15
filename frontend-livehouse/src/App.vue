<template>
  <div class="app-shell">
    <header class="site-header">
      <button class="brand" @click="go('home')">
        <span class="brand-mark">LH</span>
        <span>
          <strong>LiveHouse Tonight</strong>
          <small>把今晚留给现场</small>
        </span>
      </button>

      <nav class="nav">
        <button :class="{ active: page === 'home' }" @click="go('home')">演出</button>
        <button :class="{ active: page === 'orders' }" @click="go('orders')">订单</button>
        <button :class="{ active: page === 'tickets' }" @click="go('tickets')">票夹</button>
        <button :class="{ active: page === 'profile' }" @click="go('profile')">我的</button>
        <button :class="{ active: page === 'admin' }" @click="go('admin')">管理员端</button>
      </nav>

      <div class="user-box">
        <button v-if="user" class="avatar-button" @click="go('profile')">
          <img v-if="user.icon" :src="normalizeImage(user.icon)" alt="头像" />
          <span v-else>{{ displayName.slice(0, 1) }}</span>
        </button>
        <button v-if="user" class="ghost small" @click="logout">退出</button>
        <button v-else class="primary small" @click="go('login')">登录</button>
      </div>
    </header>

    <main>
      <section v-if="page === 'home'" class="hero">
        <div>
          <p class="eyebrow">LiveHouse Tonight</p>
          <h1>找一场值得出门的现场。</h1>
          <p class="hero-copy">热门演出、城市场馆、不同票档，一次看清楚。</p>
          <div class="search-row">
            <input v-model="filters.keyword" placeholder="搜索艺人 / 演出名" @keyup.enter="loadShows('search')" />
            <select v-model="filters.city" @change="loadShows('city')">
              <option value="">全部城市</option>
              <option>北京</option>
              <option>上海</option>
              <option>深圳</option>
              <option>广州</option>
              <option>杭州</option>
              <option>成都</option>
            </select>
            <button class="primary" @click="loadShows(filters.keyword ? 'search' : 'city')">搜索</button>
          </div>
        </div>
        <div class="hero-card">
          <span>正在售票</span>
          <strong>{{ shows.length }}</strong>
          <p>场演出</p>
        </div>
      </section>

      <section v-if="page === 'home'" class="content-grid">
        <article v-for="show in shows" :key="show.id" class="show-card" @click="openShow(show.id)">
          <div class="poster" :style="posterStyle(show.image)">
            <span>{{ show.type || '现场' }}</span>
          </div>
          <div class="show-body">
            <p class="muted">{{ formatDate(show.startTime) }}</p>
            <h3>{{ show.title }}</h3>
            <p>{{ show.artist }}</p>
            <p class="muted">{{ show.venue?.name || venueName(show.venueId) || '场馆待确认' }}</p>
          </div>
        </article>
      </section>

      <section v-if="page === 'detail'" class="detail-layout">
        <div class="detail-poster" :style="posterStyle(selectedShow?.image)"></div>
        <div class="detail-panel">
          <button class="link-button" @click="go('home')">返回</button>
          <p class="eyebrow">{{ selectedShow?.type || '演出' }}</p>
          <h2>{{ selectedShow?.title }}</h2>
          <p class="artist">{{ selectedShow?.artist }}</p>
          <p>{{ selectedShow?.description || '等一个刚好的夜晚，和喜欢的声音见面。' }}</p>
          <div class="info-list">
            <span>时间：{{ formatDate(selectedShow?.startTime) }}</span>
            <span>场馆：{{ selectedVenue?.name || '待确认' }}</span>
            <span>地址：{{ selectedVenue?.address || '待确认' }}</span>
          </div>

          <div class="ticket-list">
            <button
              v-for="ticket in ticketTypes"
              :key="ticket.id"
              :class="{ picked: pickedTicket?.id === ticket.id }"
              @click="pickedTicket = ticket"
            >
              <span>{{ ticket.name }}</span>
              <strong>¥{{ ticket.price }}</strong>
              <small>余票 {{ ticket.leftStock }} / 每人限购 {{ ticket.limitPerUser }}</small>
            </button>
          </div>

          <div class="buy-bar">
            <input v-model.number="quantity" type="number" min="1" :max="pickedTicket?.limitPerUser || 1" />
            <button class="primary" :disabled="!pickedTicket || loading" @click="buyTicket">
              {{ loading ? '请稍等...' : '立即抢票' }}
            </button>
          </div>
        </div>
      </section>

      <section v-if="page === 'orders'" class="panel">
        <div class="section-title">
          <div>
            <p class="eyebrow">Orders</p>
            <h2>我的订单</h2>
          </div>
          <button class="ghost" @click="loadOrders">刷新</button>
        </div>

        <div v-if="orders.length === 0" class="empty">还没有订单，先去挑一场喜欢的演出吧。</div>
        <article v-for="order in orders" :key="order.id" class="order-card">
          <div>
            <strong>{{ showTitle(order.showId) }}</strong>
            <p>{{ ticketName(order.ticketTypeId) }} / {{ order.quantity }} 张</p>
            <p class="muted">订单号：{{ order.id }}</p>
          </div>
          <div class="order-actions">
            <span class="status">{{ orderStatus(order) }}</span>
            <button v-if="order.payStatus === 0 && order.orderStatus !== 3" class="primary small" @click="pay(order)">
              去支付
            </button>
          </div>
        </article>
      </section>

      <section v-if="page === 'tickets'" class="panel">
        <div class="section-title">
          <div>
            <p class="eyebrow">Tickets</p>
            <h2>我的票夹</h2>
          </div>
          <button class="ghost" @click="loadMyTickets">刷新</button>
        </div>

        <div v-if="myTickets.length === 0" class="empty">支付成功后，电子票会出现在这里。</div>
        <div class="ticket-wallet">
          <article v-for="ticket in myTickets" :key="ticket.id" class="ticket-card">
            <div class="ticket-image" :style="posterStyle(showImage(ticket.showId))"></div>
            <div class="ticket-content">
              <span class="status">{{ ticket.verifyStatus === 1 ? '已使用' : '未使用' }}</span>
              <h3>{{ showTitle(ticket.showId) }}</h3>
              <p>{{ ticketName(ticket.ticketTypeId) }}</p>
              <p class="muted">{{ venueText(ticket.showId) }}</p>
              <div class="verify-code">{{ ticket.verifyCode }}</div>
            </div>
          </article>
        </div>
      </section>

      <section v-if="page === 'profile'" class="panel profile-layout">
        <div class="profile-card">
          <img v-if="profileForm.icon" :src="normalizeImage(profileForm.icon)" alt="头像" />
          <div v-else class="avatar-placeholder">{{ (profileForm.nickName || '我').slice(0, 1) }}</div>
          <h2>{{ profileForm.nickName || '未设置昵称' }}</h2>
          <p class="muted">{{ profileForm.phone }}</p>
          <input type="file" accept="image/*" @change="onAvatarChange" />
          <button class="ghost" :disabled="!avatarFile" @click="uploadAvatar">上传头像</button>
        </div>

        <div class="profile-form">
          <p class="eyebrow">Profile</p>
          <h2>个人信息</h2>
          <label>昵称<input v-model="profileForm.nickName" placeholder="给自己取个名字" /></label>
          <label>城市<input v-model="profileForm.city" placeholder="所在城市" /></label>
          <label>生日<input v-model="profileForm.birthday" type="date" /></label>
          <label>
            性别
            <select v-model.number="profileForm.gender">
              <option :value="null">不设置</option>
              <option :value="0">男</option>
              <option :value="1">女</option>
            </select>
          </label>
          <label>个人介绍<textarea v-model="profileForm.introduce" placeholder="写点喜欢的音乐、常去的场馆"></textarea></label>
          <label>新密码<input v-model="profileForm.password" type="password" placeholder="不修改可留空" /></label>
          <button class="primary" @click="saveProfile">保存资料</button>
        </div>
      </section>

      <section v-if="page === 'admin'" class="panel">
        <div class="section-title">
          <div>
            <p class="eyebrow">Admin</p>
            <h2>管理员端</h2>
          </div>
        </div>

        <div class="admin-grid">
          <div class="tool-card">
            <h3>现场核销</h3>
            <input v-model="admin.ticketCode" placeholder="输入电子票码" />
            <button class="primary" @click="verifyTicket">确认核销</button>
            <button class="ghost" @click="queryTicket">查询票券</button>
            <pre v-if="ticketInfo">{{ ticketInfo }}</pre>
          </div>

          <div class="tool-card">
            <h3>上传演出图</h3>
            <input type="file" accept="image/*" @change="onShowImageChange" />
            <button class="primary" :disabled="!showImageFile" @click="uploadShowImage">上传</button>
            <img v-if="uploadedShowUrl" :src="uploadedShowUrl" class="preview" alt="演出图预览" />
            <p v-if="uploadedShowUrl" class="muted">{{ uploadedShowUrl }}</p>
          </div>

          <div class="tool-card">
            <h3>票种库存</h3>
            <input v-model="admin.ticketTypeId" placeholder="票种ID" />
            <button class="ghost" @click="queryStock">查询</button>
          </div>

          <div class="tool-card">
            <h3>核销统计</h3>
            <input v-model="admin.showId" placeholder="演出ID" />
            <button class="ghost" @click="queryVerifyStats">查询</button>
          </div>

          <div class="tool-card">
            <h3>请求频率</h3>
            <input v-model="admin.ip" placeholder="127.0.0.1" />
            <button class="ghost" @click="queryRateLimit">查询</button>
          </div>
        </div>
      </section>

      <section v-if="page === 'login'" class="login-panel">
        <div>
          <p class="eyebrow">Login</p>
          <h2>登录后开抢</h2>
          <p class="muted">输入手机号和验证码即可进入。</p>
        </div>
        <input v-model="loginForm.phone" placeholder="手机号" />
        <input v-model="loginForm.code" placeholder="验证码" />
        <div class="actions">
          <button class="ghost" @click="sendCode">获取验证码</button>
          <button class="primary" @click="login">登录</button>
        </div>
      </section>
    </main>

    <div v-if="ticketModal.visible" class="modal-mask" @click.self="closeTicketModal">
      <section class="ticket-modal">
        <button class="close-button" @click="closeTicketModal">关闭</button>
        <div class="ticket-image large" :style="posterStyle(ticketModal.show?.image)"></div>
        <div>
          <p class="eyebrow">Tickets Ready</p>
          <h2>{{ ticketModal.show?.title || '电子票已生成' }}</h2>
          <p>{{ ticketModal.show?.artist }}</p>
          <p class="muted">{{ ticketModal.venue?.name }} {{ formatDate(ticketModal.show?.startTime) }}</p>
          <div class="modal-ticket-list">
            <div v-for="ticket in ticketModal.tickets" :key="ticket.id" class="modal-ticket-item">
              <span>{{ ticketName(ticket.ticketTypeId) }}</span>
              <strong>{{ ticket.verifyCode }}</strong>
            </div>
          </div>
          <button class="primary" @click="goTicketsFromModal">查看我的票夹</button>
        </div>
      </section>
    </div>

    <div v-if="toast.text" :class="['toast', toast.type]">{{ toast.text }}</div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, getToken, setToken } from './api'

const page = ref('home')
const loading = ref(false)
const shows = ref([])
const orders = ref([])
const myTickets = ref([])
const selectedShow = ref(null)
const selectedVenue = ref(null)
const ticketTypes = ref([])
const pickedTicket = ref(null)
const quantity = ref(1)
const user = ref(null)
const avatarFile = ref(null)
const showImageFile = ref(null)
const uploadedShowUrl = ref('')
const ticketInfo = ref('')

const showCache = reactive({})
const venueCache = reactive({})
const ticketTypeCache = reactive({})

const filters = reactive({ keyword: '', city: '' })
const loginForm = reactive({ phone: '13800138000', code: '' })
const profileForm = reactive({
  phone: '',
  nickName: '',
  icon: '',
  city: '',
  introduce: '',
  gender: null,
  birthday: '',
  password: ''
})
const admin = reactive({
  ticketCode: '',
  ticketTypeId: '1',
  showId: '1',
  ip: '127.0.0.1'
})
const toast = reactive({ text: '', type: 'success' })
const ticketModal = reactive({ visible: false, tickets: [], show: null, venue: null })

const displayName = computed(() => user.value?.nickName || '我')

function go(target) {
  page.value = target
  if (target === 'home') loadShows()
  if (target === 'orders') loadOrders()
  if (target === 'tickets') loadMyTickets()
  if (target === 'profile') loadProfile()
}

function notify(text, type = 'success') {
  toast.text = text
  toast.type = type
  window.clearTimeout(notify.timer)
  notify.timer = window.setTimeout(() => {
    toast.text = ''
  }, 2600)
}

async function call(task, successText) {
  try {
    loading.value = true
    const data = await task()
    if (successText) notify(successText)
    return data
  } catch (error) {
    notify(error.message || '请求失败', 'error')
    return null
  } finally {
    loading.value = false
  }
}

function requireLogin() {
  if (!getToken()) {
    go('login')
    notify('请先登录', 'error')
    return false
  }
  return true
}

async function loadCurrentUser() {
  if (!getToken()) return
  user.value = await call(() => api.me())
}

async function loadProfile() {
  if (!requireLogin()) return
  const profile = await call(() => api.profile())
  if (!profile) return
  Object.assign(profileForm, {
    phone: profile.phone || '',
    nickName: profile.nickName || '',
    icon: profile.icon || '',
    city: profile.city || '',
    introduce: profile.introduce || '',
    gender: profile.gender ?? null,
    birthday: profile.birthday || '',
    password: ''
  })
  user.value = { ...user.value, nickName: profile.nickName, icon: profile.icon }
}

async function loadShows(mode = 'hot') {
  const data = await call(() => {
    if (mode === 'search' && filters.keyword) return api.searchShows(filters.keyword)
    if (mode === 'city' && filters.city) return api.showsByCity(filters.city)
    return api.hotShows()
  })
  shows.value = Array.isArray(data) ? data : []
  await Promise.all(shows.value.map(show => cacheShowInfo(show)))
}

async function cacheShowInfo(show) {
  if (!show?.id) return
  showCache[show.id] = show
  if (show.venue) {
    venueCache[show.venue.id] = show.venue
  } else if (show.venueId && !venueCache[show.venueId]) {
    const venue = await call(() => api.venueDetail(show.venueId))
    if (venue) venueCache[show.venueId] = venue
  }
}

async function ensureShow(showId) {
  if (showCache[showId]) return showCache[showId]
  const show = await call(() => api.showDetail(showId))
  if (show) await cacheShowInfo(show)
  return show
}

async function ensureTicketTypes(showId) {
  const list = await call(() => api.ticketTypes(showId))
  ;(Array.isArray(list) ? list : []).forEach(ticket => {
    ticketTypeCache[ticket.id] = ticket
  })
}

function venueName(id) {
  return venueCache[id]?.name
}

async function openShow(id) {
  page.value = 'detail'
  selectedShow.value = await ensureShow(id)
  selectedVenue.value = selectedShow.value?.venue || venueCache[selectedShow.value?.venueId] || null
  pickedTicket.value = null
  quantity.value = 1

  const tickets = await call(() => api.ticketTypes(id))
  ticketTypes.value = Array.isArray(tickets) ? tickets : []
  ticketTypes.value.forEach(ticket => {
    ticketTypeCache[ticket.id] = ticket
  })
  pickedTicket.value = ticketTypes.value[0] || null
}

async function buyTicket() {
  if (!requireLogin()) return
  if (!pickedTicket.value) return notify('请选择票种', 'error')
  const data = await call(
    () => api.seckill(pickedTicket.value.id, quantity.value || 1),
    '抢票成功，请在订单中完成支付'
  )
  if (data) window.setTimeout(loadOrders, 1000)
}

async function loadOrders() {
  if (!requireLogin()) return
  const data = await call(() => api.orders())
  orders.value = Array.isArray(data) ? data : []
  await Promise.all(orders.value.map(order => ensureShow(order.showId)))
  await Promise.all([...new Set(orders.value.map(order => order.showId))].map(ensureTicketTypes))
}

async function pay(order) {
  const tickets = await call(() => api.payOrder(order.id), '支付成功')
  await loadOrders()
  if (Array.isArray(tickets) && tickets.length > 0) {
    await openTicketModal(tickets, order.showId)
  }
}

async function openTicketModal(tickets, showId) {
  const show = await ensureShow(showId)
  if (showId) await ensureTicketTypes(showId)
  ticketModal.tickets = tickets
  ticketModal.show = show
  ticketModal.venue = show?.venue || venueCache[show?.venueId] || null
  ticketModal.visible = true
}

function closeTicketModal() {
  ticketModal.visible = false
}

async function goTicketsFromModal() {
  closeTicketModal()
  go('tickets')
}

async function loadMyTickets() {
  if (!requireLogin()) return
  const data = await call(() => api.myTickets())
  myTickets.value = Array.isArray(data) ? data : []
  const showIds = [...new Set(myTickets.value.map(ticket => ticket.showId))]
  await Promise.all(showIds.map(ensureShow))
  await Promise.all(showIds.map(ensureTicketTypes))
}

async function verifyTicket() {
  if (!admin.ticketCode.trim()) return notify('请输入电子票码', 'error')
  await call(() => api.verifyTicket(admin.ticketCode.trim()), '核销成功')
  ticketInfo.value = ''
}

async function queryTicket() {
  if (!admin.ticketCode.trim()) return notify('请输入电子票码', 'error')
  const data = await call(() => api.ticketDetail(admin.ticketCode.trim()))
  ticketInfo.value = data ? JSON.stringify(data, null, 2) : ''
}

function onAvatarChange(event) {
  avatarFile.value = event.target.files?.[0] || null
}

async function uploadAvatar() {
  if (!avatarFile.value) return
  const url = await call(() => api.uploadImage(avatarFile.value, 'avatars'), '头像上传成功')
  if (url) profileForm.icon = url
}

function onShowImageChange(event) {
  showImageFile.value = event.target.files?.[0] || null
}

async function uploadShowImage() {
  if (!showImageFile.value) return
  const url = await call(() => api.uploadImage(showImageFile.value, 'shows'), '上传成功')
  if (url) uploadedShowUrl.value = url
}

async function saveProfile() {
  const payload = { ...profileForm }
  if (!payload.password) delete payload.password
  const profile = await call(() => api.updateProfile(payload), '资料已保存')
  if (profile) {
    Object.assign(profileForm, { ...profile, password: '' })
    user.value = { ...user.value, nickName: profile.nickName, icon: profile.icon }
  }
}

async function queryStock() {
  const data = await call(() => api.redisStock(admin.ticketTypeId))
  if (data !== null) notify(`当前库存：${data}`)
}

async function queryVerifyStats() {
  const data = await call(() => api.verifyStats(admin.showId))
  if (data) notify(data)
}

async function queryRateLimit() {
  const data = await call(() => api.rateLimit(admin.ip))
  if (data) notify(data)
}

async function sendCode() {
  if (!loginForm.phone) return notify('请输入手机号', 'error')
  await call(() => api.sendCode(loginForm.phone), '验证码已发送')
}

async function login() {
  if (!loginForm.phone || !loginForm.code) return notify('请输入手机号和验证码', 'error')
  const token = await call(() => api.login(loginForm), '登录成功')
  if (token) {
    setToken(token)
    await loadCurrentUser()
    go('home')
  }
}

function logout() {
  setToken('')
  user.value = null
  notify('已退出登录')
}

function orderStatus(order) {
  if (order.orderStatus === 3 || order.payStatus === 2) return '已取消'
  if (order.payStatus === 1 || order.orderStatus === 2) return '已支付'
  return '待支付'
}

function showTitle(showId) {
  return showCache[showId]?.title || `演出 ${showId}`
}

function showImage(showId) {
  return showCache[showId]?.image
}

function ticketName(ticketTypeId) {
  return ticketTypeCache[ticketTypeId]?.name || `票种 ${ticketTypeId}`
}

function venueText(showId) {
  const show = showCache[showId]
  const venue = show?.venue || venueCache[show?.venueId]
  return venue ? `${venue.name} / ${venue.address || venue.city || ''}` : '场馆待确认'
}

function formatDate(value) {
  if (!value) return '时间待定'
  return String(value).replace('T', ' ').slice(0, 16)
}

function posterStyle(image) {
  const url = normalizeImage(image)
  return url ? { backgroundImage: `url("${url}")` } : {}
}

function normalizeImage(image) {
  if (!image) return ''
  if (/^https?:\/\//.test(image) || image.startsWith('/')) return image
  return `/uploads/${image}`
}

onMounted(async () => {
  await Promise.all([loadShows(), loadCurrentUser()])
})
</script>
