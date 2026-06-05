import { createRouter, createWebHistory } from 'vue-router'
import GalleryView from '../pages/GalleryView.vue'
import { getAdminToken } from '../utils/auth'

const ProductDetailView = () => import('../pages/ProductDetailView.vue')
const CustomPageView = () => import('../pages/CustomPageView.vue')
const VoucherView = () => import('../pages/VoucherView.vue')
const AdminView = () => import('../pages/AdminView.vue')
const AdminLoginView = () => import('../pages/AdminLoginView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'gallery', component: GalleryView },
    { path: '/product/:id', name: 'product-detail', component: ProductDetailView },
    { path: '/page/:key', name: 'custom-page', component: CustomPageView },
    { path: '/voucher/:code', name: 'voucher-detail', component: VoucherView },
    { path: '/admin/login', name: 'admin-login', component: AdminLoginView },
    { path: '/admin', name: 'admin', component: AdminView },
  ],
})

router.beforeEach((to) => {
  const token = getAdminToken()
  if (to.path === '/admin/login') {
    if (token) {
      return { path: '/admin' }
    }
    return true
  }

  if (to.path.startsWith('/admin') && !token) {
    return {
      path: '/admin/login',
      query: { redirect: to.fullPath },
    }
  }

  return true
})

export default router
