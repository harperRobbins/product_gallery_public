import http from './http'

export const api = {
  publicProducts: (params) => http.get('/api/products', { params }),
  publicProductSummary: () => http.get('/api/products/summary'),
  publicProductDetail: (id) => http.get('/api/products/' + id),
  categoriesTree: () => http.get('/api/categories/tree'),

  adminLogin: (data) => http.post('/api/admin/auth/login', data),
  adminLogout: () => http.post('/api/admin/auth/logout'),
  adminMe: () => http.get('/api/admin/auth/me'),

  adminProducts: (params) => http.get('/api/admin/products', { params }),
  adminProductDetail: (id) => http.get('/api/admin/products/' + id),
  publishProduct: (data) => http.post('/api/admin/products/publish', data),
  updateProduct: (data) => http.put('/api/admin/products', data),
  updateProductTop: (id, isTop) => http.post(`/api/admin/products/${id}/top`, null, { params: { isTop } }),
  deleteProduct: (id) => http.delete('/api/admin/products/' + id),
  batchUpdateProductCategory: (data) => http.post('/api/admin/products/batch/category', data),

  adminOrderVouchers: (params) => http.get('/api/admin/order-vouchers', { params }),
  adminOrderVoucherDetail: (id) => http.get('/api/admin/order-vouchers/' + id),
  createOrderVoucher: (data) => http.post('/api/admin/order-vouchers', data),
  updateOrderVoucher: (id, data) => http.put('/api/admin/order-vouchers/' + id, data),
  voidOrderVoucher: (id) => http.post('/api/admin/order-vouchers/' + id + '/void'),
  convertOrderVoucherCurrency: (id, data) => http.post('/api/admin/order-vouchers/' + id + '/convert-currency', data),
  shareOrderVoucher: (id) => http.post('/api/admin/order-vouchers/' + id + '/share'),
  listOrderVoucherShippingAddresses: () => http.get('/api/admin/order-vouchers/shipping-addresses'),
  createOrderVoucherShippingAddress: (data) => http.post('/api/admin/order-vouchers/shipping-addresses', data),
  updateOrderVoucherShippingAddress: (id, data) => http.put('/api/admin/order-vouchers/shipping-addresses/' + id, data),
  deleteOrderVoucherShippingAddress: (id) => http.delete('/api/admin/order-vouchers/shipping-addresses/' + id),
  listOrderVoucherPaymentMethods: () => http.get('/api/admin/order-vouchers/payment-methods'),
  createOrderVoucherPaymentMethod: (data) => http.post('/api/admin/order-vouchers/payment-methods', data),
  updateOrderVoucherPaymentMethod: (id, data) => http.put('/api/admin/order-vouchers/payment-methods/' + id, data),
  deleteOrderVoucherPaymentMethod: (id) => http.delete('/api/admin/order-vouchers/payment-methods/' + id),
  publicOrderVoucherDetail: (publicCode) => http.get('/api/order-vouchers/public/' + publicCode),

  saveCategory: (data) => http.post('/api/admin/categories/save', data),
  deleteCategory: (id) => http.delete('/api/admin/categories/' + id),

  dashboardStats: (params) => http.get('/api/admin/dashboard/stats', { params }),
  adminAnalyticsOverview: (params) => http.get('/api/admin/analytics/overview', { params }),
  llmGetConfig: () => http.get('/api/admin/llm/config'),
  llmSaveConfig: (data) => http.post('/api/admin/llm/config/save', data),
  llmTestConfig: (data) => http.post('/api/admin/llm/config/test', data),
  ossGetConfig: () => http.get('/api/admin/oss/config'),
  ossSaveConfig: (data) => http.post('/api/admin/oss/config/save', data),
  ossTestConfig: (data) => http.post('/api/admin/oss/config/test', data),

  shopProfile: () => http.get('/api/shop/profile'),
  currencyRates: () => http.get('/api/currency/rates'),
  saveShopProfile: (data) => http.post('/api/admin/shop/profile', data),

  uploadImages: (files) => {
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    return http.post('/api/admin/upload/images', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  uploadVideos: (files) => {
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    return http.post('/api/admin/upload/videos', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  createShare: (productId) => http.post('/api/share/' + productId),

  wsAlbumGetConfig: () => http.get('/api/admin/ws-album/config'),
  wsAlbumSaveConfig: (data) => http.post('/api/admin/ws-album/config/save', data),
  wsAlbumTestConfig: (data) => http.post('/api/admin/ws-album/config/test', data),

  wsAlbumShopList: (params) => http.get('/api/admin/ws-album/shop/list', { params }),
  wsAlbumShopAdd: (data) => http.post('/api/admin/ws-album/shop/add', data),
  wsAlbumShopUpdate: (data) => http.post('/api/admin/ws-album/shop/update', data),
  wsAlbumShopRefresh: (data) => http.post('/api/admin/ws-album/shop/refresh', data),

  wsAlbumCrawlStart: (data) => http.post('/api/admin/ws-album/crawl/start', data),
  wsAlbumCrawlRetry: (data) => http.post('/api/admin/ws-album/crawl/retry', data),
  wsAlbumCrawlStop: (data) => http.post('/api/admin/ws-album/crawl/stop', data),
  wsAlbumCrawlLogList: (params) => http.get('/api/admin/ws-album/crawl/log/list', { params }),
  wsAlbumCrawlLogDetail: (params) => http.get('/api/admin/ws-album/crawl/log/detail', { params }),
  wsAlbumCrawlRequestLogList: (params) => http.get('/api/admin/ws-album/crawl/request-log/list', { params }),

  wsAlbumImportProductList: (params) => http.get('/api/admin/ws-album/product-import/list', { params }),
  wsAlbumImportProductDetail: (params) => http.get('/api/admin/ws-album/product-import/detail', { params }),
  wsAlbumImportProductUpdate: (data) => http.post('/api/admin/ws-album/product-import/update', data),
  wsAlbumImportProductMarkAbnormal: (data) => http.post('/api/admin/ws-album/product-import/mark-abnormal', data),
  wsAlbumImportProductDelete: (data) => http.post('/api/admin/ws-album/product-import/delete', data),
  wsAlbumImportFormal: (data) => http.post('/api/admin/ws-album/product-import/import-formal', data, { timeout: 120000 }),

  wsAlbumImportLogList: (params) => http.get('/api/admin/ws-album/import-log/list', { params }),
  wsAlbumImportLogDetail: (params) => http.get('/api/admin/ws-album/import-log/detail', { params }),
}
