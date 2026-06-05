/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      boxShadow: {
        soft: '0 10px 40px rgba(245, 158, 11, 0.16)',
      },
    },
  },
  plugins: [],
}
