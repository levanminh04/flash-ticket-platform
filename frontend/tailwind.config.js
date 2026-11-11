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
                    50: 'var(--brand-scale-50)',
                    100: 'var(--brand-scale-100)',
                    200: 'var(--brand-scale-200)',
                    300: 'var(--brand-scale-300)',
                    400: 'var(--brand-scale-400)',
                    500: 'var(--brand-scale-500)',
                    600: 'var(--brand-scale-600)',
                    700: 'var(--brand-scale-700)',
                    800: 'var(--brand-scale-800)',
                    900: 'var(--brand-scale-900)',
                },
            },
        },
    },
    plugins: [],
}
