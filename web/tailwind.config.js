/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#8B5CF6',
          light: '#A78BFA',
          dark: '#7C3AED',
        },
        secondary: '#06B6D4',
        accent: '#F472B6',
        text: {
          DEFAULT: '#F8FAFC',
          secondary: '#94A3B8',
          muted: '#64748B',
        },
        background: {
          DEFAULT: '#0F172A',
          light: '#1E293B',
        },
        surface: {
          DEFAULT: '#1E293B',
          light: '#334155',
        },
        border: '#334155',
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
      animation: {
        'float': 'float 3s ease-in-out infinite',
        'pulse-glow': 'pulse-glow 2s ease-in-out infinite',
        'shimmer': 'shimmer 2s infinite',
      },
      boxShadow: {
        'glow': '0 0 40px rgba(139, 92, 246, 0.3)',
        'glow-sm': '0 0 20px rgba(139, 92, 246, 0.2)',
        'glow-lg': '0 0 60px rgba(139, 92, 246, 0.4)',
        'glow-cyan': '0 0 40px rgba(6, 182, 212, 0.3)',
      },
    },
  },
  plugins: [],
}

