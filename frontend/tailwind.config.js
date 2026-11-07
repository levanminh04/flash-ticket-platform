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
                    50: '#fff9e6',
                    100: '#fff4cc',
                    200: '#ffe18a',
                    300: '#f5cb4f',
                    400: '#dfb329',
                    500: '#cc9900',
                    600: '#946b00',
                    700: '#805c00',
                    800: '#614700',
                    900: '#493600',
                },
            },
        },
    },
    plugins: [],
}
