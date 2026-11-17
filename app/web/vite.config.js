import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, new URL('..', import.meta.url).pathname, '')
  const value = (key, fallback) => process.env[key] || env[key] || fallback
  const appHost = value('APP_LISTEN_HOST', '0.0.0.0')
  const appPort = Number(value('APP_DEV_PORT', '5173'))
  const apiHost = value('API_LISTEN_HOST', '0.0.0.0')
  const proxyHost = apiHost === '0.0.0.0' || apiHost === '::' ? '127.0.0.1' : apiHost
  const purchasesPort = value('PURCHASES_API_PORT', '8080')
  const analyticsPort = value('ANALYTICS_API_PORT', '8081')

  return {
    server: {
      host: appHost,
      port: appPort,
      strictPort: true,
      proxy: {
        '/api/purchases': value('PURCHASES_API_URL', `http://${proxyHost}:${purchasesPort}`),
        '/api/auth': value('PURCHASES_API_URL', `http://${proxyHost}:${purchasesPort}`),
        '/api/integrations': value('PURCHASES_API_URL', `http://${proxyHost}:${purchasesPort}`),
        '/api/farms': value('PURCHASES_API_URL', `http://${proxyHost}:${purchasesPort}`),
        '/api/users': value('PURCHASES_API_URL', `http://${proxyHost}:${purchasesPort}`),
        '/api/analytics': value('ANALYTICS_API_URL', `http://${proxyHost}:${analyticsPort}`),
        '/swagger-ui': value('PURCHASES_API_URL', `http://${proxyHost}:${purchasesPort}`),
        '/v3/api-docs': value('PURCHASES_API_URL', `http://${proxyHost}:${purchasesPort}`),
        '/webjars': value('PURCHASES_API_URL', `http://${proxyHost}:${purchasesPort}`),
        '/q': value('ANALYTICS_API_URL', `http://${proxyHost}:${analyticsPort}`),
      },
    },
    preview: { host: appHost, port: appPort },
  }
})
