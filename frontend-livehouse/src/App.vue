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
              <div class="ticket-badge-row">
                <span class="status" :class="{ used: ticket.verifyStatus === 1 }">
                  {{ ticket.verifyStatus === 1 ? '已核销' : '待核销' }}
                </span>
                <span class="ticket-type-label">{{ ticketName(ticket.ticketTypeId) }}</span>
              </div>
              <h3 class="ticket-title">{{ showTitle(ticket.showId) }}</h3>
              <p class="muted ticket-meta">{{ venueText(ticket.showId) }}</p>
              
              <div class="ticket-actions">
                <button 
                  class="qr-button" 
                  @click="showQRCode(ticket)" 
                  :disabled="ticket.verifyStatus === 1">
                  {{ ticket.verifyStatus === 1 ? '已核销入场' : '点击查看二维码' }}
                </button>
              </div>
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

          <div class="tool-card data-screen-card">
            <h3>数据大屏</h3>
            <p class="muted">查看系统运营数据和统计分析</p>
            <button class="primary" @click="toggleDataScreen">{{ showDataScreen ? '关闭数据大屏' : '打开数据大屏' }}</button>
          </div>
        </div>

        <!-- 数据大屏展示区域 -->
        <div v-if="showDataScreen" class="data-screen-panel">
          <div class="data-screen-header">
            <h2>数据大屏</h2>
            <div class="screen-controls">
              <button class="ghost small" @click="refreshDataScreen">刷新数据</button>
              <button class="ghost small" @click="toggleAutoRefresh">{{ autoRefresh ? '停止自动刷新' : '开启自动刷新' }}</button>
            </div>
          </div>

          <div v-if="dataScreenLoading" class="loading-state">
            <p>正在加载数据...</p>
          </div>

          <div v-else class="data-screen-grid">
            <!-- 总体概览 -->
            <div class="data-card overview-card">
              <h3>总体概览</h3>
              <div v-if="dataScreen.总体概览" class="stats-grid">
                <div class="stat-item" v-for="(value, key) in dataScreen.总体概览" :key="key">
                  <span class="stat-label">{{ key }}</span>
                  <span class="stat-value">{{ formatValue(value) }}</span>
                </div>
              </div>
            </div>

            <!-- 营收分析 -->
            <div class="data-card revenue-card large-card">
              <h3>💰 营收分析</h3>
              <div v-if="dataScreen.营收分析" class="revenue-stats">
                <div class="revenue-metrics">
                  <div class="metric-item" v-for="(value, key) in dataScreen.营收分析" :key="key" v-show="key !== '近7天营收趋势'">
                    <span class="metric-label">{{ key }}</span>
                    <span class="metric-value">¥{{ formatMoney(value) }}</span>
                  </div>
                </div>
                <div class="chart-container">
                  <h4>📈 营收趋势图</h4>
                  <canvas ref="revenueChartCanvas" class="chart-canvas"></canvas>
                </div>
              </div>
            </div>

            <!-- 演出统计 -->
            <div class="data-card shows-card large-card">
              <h3>🎭 演出统计</h3>
              <div v-if="dataScreen.演出统计">
                <div v-if="dataScreen.演出统计['热门演出TOP5']" class="top-shows">
                  <div class="chart-container">
                    <h4>🏆 热门演出TOP5 - 营收排行</h4>
                    <canvas ref="showRankingChartCanvas" class="chart-canvas"></canvas>
                  </div>
                  <div class="shows-details">
                    <div v-for="(show, index) in dataScreen.演出统计['热门演出TOP5']" :key="show.演出ID" class="show-item">
                      <span class="rank rank-{{ index + 1 }}">{{ index + 1 }}</span>
                      <div class="show-info">
                        <span class="show-title">{{ show.演出标题 }}</span>
                        <span class="show-artist">{{ show.艺人 }}</span>
                        <div class="show-metrics">
                          <span class="show-revenue">¥{{ formatMoney(show.营收) }}</span>
                          <span class="show-tickets">{{ show.已售票数 }}张</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 订单统计 -->
            <div class="data-card orders-card large-card">
              <h3>📋 订单统计</h3>
              <div v-if="dataScreen.订单统计" class="order-stats">
                <div class="order-today">
                  <div class="stat-row">
                    <span>今日订单数</span>
                    <span class="highlight-number">{{ dataScreen.订单统计.今日订单数 }}</span>
                  </div>
                  <div class="stat-row">
                    <span>今日支付订单数</span>
                    <span class="highlight-number">{{ dataScreen.订单统计.今日支付订单数 }}</span>
                  </div>
                </div>
                
                <div class="charts-row">
                  <div class="chart-section">
                    <h4>📊 订单状态分布</h4>
                    <canvas ref="orderStatusChartCanvas" class="chart-canvas"></canvas>
                  </div>
                  <div class="chart-section">
                    <h4>📈 24小时订单趋势</h4>
                    <canvas ref="hourlyOrderChartCanvas" class="chart-canvas"></canvas>
                  </div>
                </div>
              </div>
            </div>

            <!-- 限流统计 -->
            <div class="data-card ratelimit-card">
              <h3>限流统计</h3>
              <div v-if="dataScreen.限流统计" class="ratelimit-stats">
                <div v-if="dataScreen.限流统计.限流统计" class="ratelimit-info">
                  <div class="info-item" v-for="(value, key) in dataScreen.限流统计.限流统计" :key="key" v-show="key !== '近期限流记录'">
                    <span>{{ key }}</span>
                    <span>{{ value }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 访问统计 -->
            <div class="data-card visits-card large-card">
              <h3>👥 访问统计</h3>
              <div v-if="dataScreen.访问统计 && dataScreen.访问统计.网站访问统计" class="visit-stats">
                <div class="visit-overview">
                  <div class="visit-item">
                    <span>今日访问量</span>
                    <span class="highlight-number">{{ dataScreen.访问统计.网站访问统计.今日访问量 }}</span>
                  </div>
                  <div class="visit-item">
                    <span>今日独立访客</span>
                    <span class="highlight-number">{{ dataScreen.访问统计.网站访问统计.今日独立访客 }}</span>
                  </div>
                  <div class="visit-item">
                    <span>实时在线用户</span>
                    <span class="highlight-number online-users">{{ dataScreen.访问统计.网站访问统计.实时在线用户 }}</span>
                  </div>
                </div>
                <div class="chart-container">
                  <h4>📊 近7天访问趋势</h4>
                  <canvas ref="visitTrendChartCanvas" class="chart-canvas"></canvas>
                </div>
              </div>
            </div>

            <!-- 实时数据 -->
            <div class="data-card realtime-card">
              <h3>实时数据</h3>
              <div v-if="dataScreen.实时数据" class="realtime-stats">
                <div class="update-time">
                  <small>更新时间: {{ dataScreen.实时数据.数据更新时间 }}</small>
                </div>
                <div v-if="dataScreen.实时数据.实时指标" class="realtime-metrics">
                  <div v-for="(value, key) in dataScreen.实时数据.实时指标" :key="key" class="metric-item">
                    <span>{{ key }}</span>
                    <span>{{ formatValue(value) }}</span>
                  </div>
                </div>
              </div>
            </div>
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
        <button class="close-button" @click="closeTicketModal" aria-label="关闭">×</button>
        <div class="ticket-image large" :style="posterStyle(ticketModal.show?.image)"></div>
        <div class="ticket-modal-body">
          <p class="eyebrow">购票成功</p>
          <h2>{{ ticketModal.show?.title || '演出门票已出票' }}</h2>
          <p class="artist">{{ ticketModal.show?.artist }}</p>
          <p class="muted">{{ ticketModal.venue?.name }} · {{ formatDate(ticketModal.show?.startTime) }}</p>
          <div class="modal-ticket-list">
            <div v-for="(ticket, idx) in ticketModal.tickets" :key="ticket.id" class="modal-ticket-item">
              <span class="modal-ticket-name">{{ ticketName(ticket.ticketTypeId) }}</span>
              <span class="modal-ticket-badge">第 {{ idx + 1 }} 张 · 出票成功</span>
            </div>
          </div>
          <button class="primary full-width" @click="goTicketsFromModal">前往我的票夹查看二维码</button>
        </div>
      </section>
    </div>

    <!-- 二维码模态框 -->
    <div v-if="qrModal.visible" class="modal-mask" @click.self="closeQRModal">
      <section class="qr-modal">
        <button class="close-button" @click="closeQRModal" aria-label="关闭">×</button>
        <div class="qr-content">
          <div class="qr-header">
            <p class="eyebrow">入场凭证</p>
            <h2>{{ showTitle(qrModal.ticket?.showId) }}</h2>
            <p class="qr-ticket-type">{{ ticketName(qrModal.ticket?.ticketTypeId) }}</p>
            <p class="muted qr-meta">{{ formatDate(showCache[qrModal.ticket?.showId]?.startTime) }}</p>
            <p class="muted qr-meta">{{ venueText(qrModal.ticket?.showId) }}</p>
          </div>
          
          <div class="qr-code-container">
            <canvas ref="qrCanvas" class="qr-code"></canvas>
            <p class="qr-tip">入场时请向现场工作人员出示此二维码</p>
          </div>
        </div>
      </section>
    </div>

    <div v-if="toast.text" :class="['toast', toast.type]">{{ toast.text }}</div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, nextTick } from 'vue'
import QRCode from 'qrcode'
import { api, getToken, setToken } from './api'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  Filler
} from 'chart.js'

// 注册Chart.js组件
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  Filler
)

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
const qrCanvas = ref(null)

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

// 数据大屏相关状态
const showDataScreen = ref(false)
const dataScreenLoading = ref(false)
const autoRefresh = ref(false)
const refreshTimer = ref(null)
const dataScreen = reactive({})

// 图表相关refs
const revenueChartCanvas = ref(null)
const orderStatusChartCanvas = ref(null)
const showRankingChartCanvas = ref(null)
const visitTrendChartCanvas = ref(null)
const hourlyOrderChartCanvas = ref(null)

// 图表实例
let revenueChart = null
let orderStatusChart = null
let showRankingChart = null
let visitTrendChart = null
let hourlyOrderChart = null

const toast = reactive({ text: '', type: 'success' })
const ticketModal = reactive({ visible: false, tickets: [], show: null, venue: null })
const qrModal = reactive({ visible: false, ticket: null })

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

async function showQRCode(ticket) {
  if (ticket.verifyStatus === 1) {
    notify('此票已使用', 'error')
    return
  }
  
  qrModal.ticket = ticket
  qrModal.visible = true
  
  // 等待DOM更新后生成二维码
  await nextTick()
  generateQRCode(ticket.verifyCode)
}

function closeQRModal() {
  qrModal.visible = false
  qrModal.ticket = null
}

async function generateQRCode(ticketCode) {
  if (!qrCanvas.value) return
  
  try {
    await QRCode.toCanvas(qrCanvas.value, ticketCode, {
      width: 190,
      margin: 1,
      color: {
        dark: '#1e1c24',
        light: '#ffffff'
      }
    })
  } catch (error) {
    console.error('二维码生成失败:', error)
    notify('二维码生成失败', 'error')
  }
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

// ========== 数据大屏相关函数 ==========

async function toggleDataScreen() {
  showDataScreen.value = !showDataScreen.value
  if (showDataScreen.value) {
    await loadDataScreenData()
  } else {
    // 关闭数据大屏时停止自动刷新和销毁图表
    if (autoRefresh.value) {
      toggleAutoRefresh()
    }
    destroyExistingCharts()
  }
}

async function loadDataScreenData() {
  dataScreenLoading.value = true
  try {
    const data = await call(() => api.getDataScreenAll())
    if (data) {
      Object.assign(dataScreen, data)
      notify('数据加载成功')
      
      // 等待DOM更新后创建图表
      await nextTick()
      createCharts()
    }
  } catch (error) {
    notify('数据加载失败：' + error.message, 'error')
  } finally {
    dataScreenLoading.value = false
  }
}

async function refreshDataScreen() {
  if (!showDataScreen.value) return
  await loadDataScreenData()
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  
  if (autoRefresh.value) {
    // 开启自动刷新，每30秒刷新一次
    refreshTimer.value = setInterval(() => {
      refreshDataScreen()
    }, 30000)
    notify('已开启自动刷新（30秒一次）')
  } else {
    // 关闭自动刷新
    if (refreshTimer.value) {
      clearInterval(refreshTimer.value)
      refreshTimer.value = null
    }
    notify('已停止自动刷新')
  }
}

// 数据格式化工具函数
function formatValue(value) {
  if (typeof value === 'number') {
    if (value > 10000) {
      return (value / 10000).toFixed(1) + '万'
    }
    return value.toLocaleString()
  }
  return value || '-'
}

function formatMoney(amount) {
  if (!amount || isNaN(amount)) return '0.00'
  return Number(amount).toLocaleString('zh-CN', { 
    minimumFractionDigits: 2, 
    maximumFractionDigits: 2 
  })
}

function getBarHeight(value, dataArray) {
  if (!dataArray || dataArray.length === 0) return 0
  const maxValue = Math.max(...dataArray.map(item => Number(item.营收 || 0)))
  if (maxValue === 0) return 0
  return (Number(value || 0) / maxValue) * 100
}

// ========== 图表创建和管理函数 ==========

function destroyExistingCharts() {
  // 销毁现有图表实例
  if (revenueChart) {
    revenueChart.destroy()
    revenueChart = null
  }
  if (orderStatusChart) {
    orderStatusChart.destroy()
    orderStatusChart = null
  }
  if (showRankingChart) {
    showRankingChart.destroy()
    showRankingChart = null
  }
  if (visitTrendChart) {
    visitTrendChart.destroy()
    visitTrendChart = null
  }
  if (hourlyOrderChart) {
    hourlyOrderChart.destroy()
    hourlyOrderChart = null
  }
}

function createCharts() {
  // 先销毁现有图表
  destroyExistingCharts()
  
  // 创建各个图表
  createRevenueChart()
  createOrderStatusChart()
  createShowRankingChart()
  createVisitTrendChart()
  createHourlyOrderChart()
}

function createRevenueChart() {
  if (!revenueChartCanvas.value || !dataScreen.营收分析?.['近7天营收趋势']) return
  
  const trendData = dataScreen.营收分析['近7天营收趋势']
  const ctx = revenueChartCanvas.value.getContext('2d')
  
  revenueChart = new ChartJS(ctx, {
    type: 'line',
    data: {
      labels: trendData.map(item => item.日期),
      datasets: [{
        label: '营收 (元)',
        data: trendData.map(item => item.营收),
        borderColor: '#10B981',
        backgroundColor: 'rgba(16, 185, 129, 0.1)',
        borderWidth: 3,
        fill: true,
        tension: 0.4,
        pointBackgroundColor: '#10B981',
        pointBorderColor: '#ffffff',
        pointBorderWidth: 2,
        pointRadius: 6,
        pointHoverRadius: 8
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          backgroundColor: 'rgba(0, 0, 0, 0.8)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: '#10B981',
          borderWidth: 1,
          callbacks: {
            label: function(context) {
              return '营收: ¥' + context.parsed.y.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
            }
          }
        }
      },
      scales: {
        x: {
          grid: {
            display: false
          },
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              size: 12,
              weight: '600'
            }
          }
        },
        y: {
          grid: {
            color: 'rgba(255, 255, 255, 0.1)'
          },
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              size: 12,
              weight: '600'
            },
            callback: function(value) {
              return '¥' + (value / 1000).toFixed(0) + 'K'
            }
          }
        }
      },
      animation: {
        duration: 2000,
        easing: 'easeInOutQuart'
      }
    }
  })
}

function createOrderStatusChart() {
  if (!orderStatusChartCanvas.value || !dataScreen.订单统计?.订单状态分布) return
  
  const statusData = dataScreen.订单统计.订单状态分布
  const labels = Object.keys(statusData)
  const data = Object.values(statusData)
  const ctx = orderStatusChartCanvas.value.getContext('2d')
  
  const colors = ['#F59E0B', '#10B981', '#EF4444', '#8B5CF6']
  
  orderStatusChart = new ChartJS(ctx, {
    type: 'doughnut',
    data: {
      labels: labels,
      datasets: [{
        data: data,
        backgroundColor: colors,
        borderColor: '#ffffff',
        borderWidth: 3,
        hoverBorderWidth: 4
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            color: 'rgba(255, 255, 255, 0.9)',
            font: {
              size: 12,
              weight: '600'
            },
            padding: 15,
            usePointStyle: true,
            pointStyle: 'circle'
          }
        },
        tooltip: {
          backgroundColor: 'rgba(0, 0, 0, 0.8)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          callbacks: {
            label: function(context) {
              const total = context.dataset.data.reduce((a, b) => a + b, 0)
              const percent = ((context.parsed / total) * 100).toFixed(1)
              return context.label + ': ' + context.parsed + ' (' + percent + '%)'
            }
          }
        }
      },
      cutout: '60%',
      animation: {
        animateRotate: true,
        duration: 2000
      }
    }
  })
}

function createShowRankingChart() {
  if (!showRankingChartCanvas.value || !dataScreen.演出统计?.['热门演出TOP5']) return
  
  const showData = dataScreen.演出统计['热门演出TOP5']
  const ctx = showRankingChartCanvas.value.getContext('2d')
  
  showRankingChart = new ChartJS(ctx, {
    type: 'bar',
    data: {
      labels: showData.map(show => show.演出标题.length > 8 ? show.演出标题.substring(0, 8) + '...' : show.演出标题),
      datasets: [{
        label: '营收 (元)',
        data: showData.map(show => show.营收),
        backgroundColor: [
          'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          'rgba(102, 126, 234, 0.8)',
          'rgba(118, 75, 162, 0.8)',
          'rgba(102, 126, 234, 0.6)',
          'rgba(118, 75, 162, 0.6)'
        ],
        borderColor: '#667eea',
        borderWidth: 2,
        borderRadius: 8,
        borderSkipped: false
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          backgroundColor: 'rgba(0, 0, 0, 0.8)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          callbacks: {
            label: function(context) {
              return '营收: ¥' + context.parsed.x.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
            }
          }
        }
      },
      scales: {
        x: {
          grid: {
            color: 'rgba(255, 255, 255, 0.1)'
          },
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              size: 11,
              weight: '600'
            },
            callback: function(value) {
              return '¥' + (value / 1000).toFixed(0) + 'K'
            }
          }
        },
        y: {
          grid: {
            display: false
          },
          ticks: {
            color: 'rgba(255, 255, 255, 0.9)',
            font: {
              size: 11,
              weight: '600'
            }
          }
        }
      },
      animation: {
        duration: 2000,
        easing: 'easeInOutQuart'
      }
    }
  })
}

function createVisitTrendChart() {
  if (!visitTrendChartCanvas.value || !dataScreen.访问统计?.网站访问统计?.['近7天访问趋势']) return
  
  const trendData = dataScreen.访问统计.网站访问统计['近7天访问趋势']
  const ctx = visitTrendChartCanvas.value.getContext('2d')
  
  visitTrendChart = new ChartJS(ctx, {
    type: 'line',
    data: {
      labels: trendData.map(item => item.日期),
      datasets: [{
        label: '访问量',
        data: trendData.map(item => item.访问量),
        borderColor: '#8B5CF6',
        backgroundColor: 'rgba(139, 92, 246, 0.1)',
        borderWidth: 3,
        fill: true,
        tension: 0.4,
        pointBackgroundColor: '#8B5CF6',
        pointBorderColor: '#ffffff',
        pointBorderWidth: 2,
        pointRadius: 5,
        pointHoverRadius: 7
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          backgroundColor: 'rgba(0, 0, 0, 0.8)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: '#8B5CF6',
          borderWidth: 1
        }
      },
      scales: {
        x: {
          grid: {
            color: 'rgba(139, 92, 246, 0.1)'
          },
          ticks: {
            color: '#4B5563',
            font: {
              size: 12,
              weight: '600'
            }
          }
        },
        y: {
          grid: {
            color: 'rgba(139, 92, 246, 0.1)'
          },
          ticks: {
            color: '#4B5563',
            font: {
              size: 12,
              weight: '600'
            }
          }
        }
      },
      animation: {
        duration: 2000,
        easing: 'easeInOutQuart'
      }
    }
  })
}

function createHourlyOrderChart() {
  if (!hourlyOrderChartCanvas.value || !dataScreen.订单统计?.['24小时订单趋势']) return
  
  const hourlyData = dataScreen.订单统计['24小时订单趋势']
  const ctx = hourlyOrderChartCanvas.value.getContext('2d')
  
  hourlyOrderChart = new ChartJS(ctx, {
    type: 'line',
    data: {
      labels: hourlyData.map(item => item.时间),
      datasets: [{
        label: '订单数',
        data: hourlyData.map(item => item.订单数),
        borderColor: '#F59E0B',
        backgroundColor: 'rgba(245, 158, 11, 0.1)',
        borderWidth: 2,
        fill: true,
        tension: 0.3,
        pointBackgroundColor: '#F59E0B',
        pointBorderColor: '#ffffff',
        pointBorderWidth: 2,
        pointRadius: 3,
        pointHoverRadius: 5
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          backgroundColor: 'rgba(0, 0, 0, 0.8)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: '#F59E0B',
          borderWidth: 1
        }
      },
      scales: {
        x: {
          grid: {
            display: false
          },
          ticks: {
            color: 'rgba(255, 255, 255, 0.7)',
            font: {
              size: 10,
              weight: '600'
            },
            maxTicksLimit: 8
          }
        },
        y: {
          grid: {
            color: 'rgba(255, 255, 255, 0.1)'
          },
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              size: 11,
              weight: '600'
            }
          }
        }
      },
      animation: {
        duration: 1500,
        easing: 'easeInOutQuart'
      }
    }
  })
}

onMounted(async () => {
  await Promise.all([loadShows(), loadCurrentUser()])
})
</script>
