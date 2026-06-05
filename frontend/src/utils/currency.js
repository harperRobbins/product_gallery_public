export const CURRENCY_STORAGE_KEY = 'gallery_currency'
export const CURRENCY_CNY = 'CNY'
export const CURRENCY_USD = 'USD'
export const CURRENCY_EUR = 'EUR'
export const CURRENCY_GBP = 'GBP'
export const CURRENCY_JPY = 'JPY'

export const COMMON_CURRENCIES = [
  { code: CURRENCY_USD, symbol: '$', label: 'USD' },
  { code: CURRENCY_EUR, symbol: '€', label: 'EUR' },
  { code: CURRENCY_GBP, symbol: '£', label: 'GBP' },
  { code: CURRENCY_JPY, symbol: '¥', label: 'JPY' },
  { code: CURRENCY_CNY, symbol: '¥', label: 'CNY' },
]

const CURRENCY_SYMBOLS = COMMON_CURRENCIES.reduce((acc, row) => {
  acc[row.code] = row.symbol
  return acc
}, {})

const FALLBACK_RATES = {
  CNY: 1,
  USD: 0.14,
  EUR: 0.13,
  GBP: 0.11,
  JPY: 22,
}

export const normalizeCurrency = (currency) => {
  const raw = String(currency || '').trim().toUpperCase()
  if (!raw) return CURRENCY_USD
  if (COMMON_CURRENCIES.some((item) => item.code === raw)) return raw
  return CURRENCY_USD
}

export const defaultCurrencyByLang = (lang) => {
  const raw = String(lang || '').trim().toLowerCase()
  if (raw.startsWith('zh')) return CURRENCY_CNY
  return CURRENCY_USD
}

export const getCurrentCurrency = (lang) => {
  try {
    const stored = localStorage.getItem(CURRENCY_STORAGE_KEY)
    if (stored) return normalizeCurrency(stored)
  } catch (_error) {
  }
  return defaultCurrencyByLang(lang)
}

export const setCurrentCurrency = (currency) => {
  const normalized = normalizeCurrency(currency)
  try {
    localStorage.setItem(CURRENCY_STORAGE_KEY, normalized)
  } catch (_error) {
  }
  return normalized
}

const normalizeNumber = (value) => {
  const num = Number(value || 0)
  if (!Number.isFinite(num) || num <= 0) return 0
  return num
}

const formatAmount = (value) => {
  const rounded = Math.round(value * 100) / 100
  if (Math.abs(rounded - Math.round(rounded)) < 0.000001) {
    return String(Math.round(rounded))
  }
  return rounded.toFixed(2)
}

const resolveRate = (rates, currencyCode) => {
  const normalizedCode = normalizeCurrency(currencyCode)
  const fromRemote = rates && rates[normalizedCode] != null ? Number(rates[normalizedCode]) : NaN
  if (Number.isFinite(fromRemote) && fromRemote > 0) return fromRemote
  const fallback = Number(FALLBACK_RATES[normalizedCode])
  if (Number.isFinite(fallback) && fallback > 0) return fallback
  return normalizedCode === CURRENCY_CNY ? 1 : 0
}

export const formatPriceByCurrency = (price, currency, rates, sourceCurrency = CURRENCY_CNY) => {
  const normalized = normalizeCurrency(currency)
  const sourcePrice = normalizeNumber(price)
  if (sourcePrice <= 0) {
    return {
      code: normalized,
      symbol: CURRENCY_SYMBOLS[normalized] || '¥',
      text: '0',
      value: 0,
    }
  }
  const sourceRate = resolveRate(rates, sourceCurrency)
  const targetRate = resolveRate(rates, normalized)
  const converted = sourceRate > 0 && targetRate > 0
    ? sourcePrice / sourceRate * targetRate
    : sourcePrice
  return {
    code: normalized,
    symbol: CURRENCY_SYMBOLS[normalized] || '¥',
    text: formatAmount(converted),
    value: converted,
  }
}
