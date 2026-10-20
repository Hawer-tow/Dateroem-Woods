import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// "access the website from another network ... testing how the website
// behaves in the mobile set up" - a phone reaching this dev server
// through an ngrok tunnel needs three things a same-machine browser never
// needed: host:true (bind 0.0.0.0, not just localhost, so the tunnel can
// actually reach it), allowedHosts:true (Vite blocks unrecognized Host
// headers by default - the tunnel's random subdomain would otherwise get
// a "This host is not allowed" error), and the proxy below, so the phone
// only ever talks to the one tunneled origin - VITE_API_BASE_URL is left
// empty for this, and Vite's own dev server (which, unlike the phone, can
// actually reach localhost:8000) forwards those requests to the BFF
// itself. That also sidesteps needing a second public tunnel and any
// cross-origin complications.
// 127.0.0.1, not "localhost": Node resolves "localhost" to IPv6 (::1)
// first on this machine, which has no route here, so every proxied
// request used to eat a ~20s stall before falling back to IPv4 and
// finally succeeding - slow enough that it read as a failure ("why not
// provide a valid answer?" - the AI call was fine, this proxy hop wasn't).
const BFF_TARGET = 'http://127.0.0.1:8000'
const BACKEND_TARGET = 'http://127.0.0.1:8080'
// "the url order/39 is not good and refreshing it here makes the website
// crash" - traced to /orders/:orderId (a page route, wants index.html)
// sharing its path with the /orders proxy prefix (for api.getOrder()'s
// XHR, which wants the BFF's JSON). Vite's proxy can't tell the two
// apart by path alone, so ANY full navigation - a refresh, a pasted
// link, typing the URL - to /orders/39, /products/7, /shipping, /help or
// /marketing got silently swallowed by the proxy and forwarded to the
// BFF instead of the SPA, coming back as raw JSON (or a plain 401/404)
// with no HTML, no CSS, no React - the "crash". Every BFF-bound call now
// goes under the single /svc prefix (stripped before reaching the BFF),
// which no page route will ever collide with, so the SPA fallback always
// wins for a real page request.
const PROXY = {
  '/svc': { target: BFF_TARGET, rewrite: (path) => path.replace(/^\/svc/, '') },
  // Product/campaign image and video URLs (backend_client.py rewrites
  // them to this relative form) - served by the BFF's own /media mount.
  // Never requested as a page route, so no collision risk here.
  '/media': BFF_TARGET,
  // "servers seem to have a problem ... from the database end" - traced
  // to WSL2's Windows-side localhost relay not forwarding port 8080
  // reliably (a separate, lower-level issue from anything in this repo).
  // Vite itself runs inside the same WSL VM as the backend, so it reaches
  // it over internal loopback regardless of whether Windows can - routing
  // Safaricom's M-Pesa callback through here instead of a direct 8080
  // tunnel sidesteps that relay entirely, and means one tunnel can serve
  // both the website and the callback again.
  // Only the callback path, not all of /api: the rest of the backend
  // trusts the X-Actor-User-Id header from the servers that sit in front
  // of it, so exposing it through the public tunnel let anyone act as any
  // account. Every legitimate caller (both website/mobile servers, the
  // desktop's admin server) reaches the backend directly on this machine.
  '/api/dateroem-mpesa-callback': BACKEND_TARGET,
}

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    allowedHosts: true,
    proxy: PROXY,
  },
  // "the website keep reloading a white page" - traced to the dev
  // server's own HMR websocket + every file edit pushing a live reload to
  // whatever's connected through the tunnel, plus every dev-server
  // restart briefly dropping the connection. `npm run preview` serves the
  // real production build instead - no HMR, no file watcher, stable for
  // actually testing against - so it needs this block since Vite's
  // preview server doesn't inherit the `server` options above.
  preview: {
    host: true,
    port: 5173,
    allowedHosts: true,
    proxy: PROXY,
  },
})
