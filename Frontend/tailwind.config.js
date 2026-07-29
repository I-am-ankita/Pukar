/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        maroon: {
          DEFAULT: '#7B1E1E',
          50: '#FBF3F3', 100: '#F5E0E0', 200: '#E8B9B9',
          300: '#D98A8A', 400: '#C25151', 500: '#A93333',
          600: '#7B1E1E', 700: '#651818', 800: '#4F1313', 900: '#3A0E0E',
        },
        cream: '#FAF7F2',
        ink: '#0F1729',
        flag: '#DC2626',
        verify: '#16A34A',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
    },
  },
  plugins: [],
};
