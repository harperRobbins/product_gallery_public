const TOKEN_KEY = 'admin_token'
const USERNAME_KEY = 'admin_username'

export const getAdminToken = () => localStorage.getItem(TOKEN_KEY) || ''

export const getAdminUsername = () => localStorage.getItem(USERNAME_KEY) || ''

export const saveAdminAuth = (token, username) => {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  }
  if (username) {
    localStorage.setItem(USERNAME_KEY, username)
  }
}

export const clearAdminAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
}
