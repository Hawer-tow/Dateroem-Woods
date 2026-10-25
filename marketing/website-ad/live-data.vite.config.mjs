// The website build for filming adverts, with the LIVE shop's data: products,
// pickup areas, campaigns, pictures and the assistant come straight from
// https://dateroemwoods.co.ke, so the advert shows exactly what customers see
// (25 October 2026). Only reading goes through: driver.mjs answers the
// sign-in, order and payment requests itself with a sample order and refuses
// every other write, so filming never changes anything on the live site.
// (A plain object: this folder has no node_modules of its own to import from.)
const LIVE = 'https://dateroemwoods.co.ke'

export default {
  preview: {
    host: '127.0.0.1',
    port: 4192,
    strictPort: true,
    proxy: {
      '/svc': { target: LIVE, changeOrigin: true, secure: true },
      '/media': { target: LIVE, changeOrigin: true, secure: true },
    },
  },
}
