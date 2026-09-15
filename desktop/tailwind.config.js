/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Segoe UI"', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Cascadia Code', 'Consolas', 'monospace'],
      },
      colors: {
        surface: {
          DEFAULT: '#12141a',
          raised: '#1a1d26',
          overlay: '#222632',
        },
        accent: {
          DEFAULT: '#3d8bfd',
          muted: '#2a5fad',
        },
      },
    },
  },
  plugins: [],
}
