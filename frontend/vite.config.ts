import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')
const fromConfigDir = (relativePath: string) =>
    decodeURIComponent(new URL(relativePath, import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1')

export default defineConfig(({ mode }) => {
    const repoRoot = fromConfigDir('../')
    const env = loadEnv(mode, repoRoot, 'VITE_')

    const apiGatewayUrl = env.VITE_API_GATEWAY_URL && trimTrailingSlash(env.VITE_API_GATEWAY_URL)

    return {
        envDir: repoRoot,
        plugins: [react()],
        resolve: {
            alias: {
                '@': fromConfigDir('./src'),
            },
        },
        server: {
            port: 5173,
            proxy: {
                ...(apiGatewayUrl ? { '/api': {
                    target: apiGatewayUrl,
                    changeOrigin: true,
                } } : {}),
            },
        },
    }
})
