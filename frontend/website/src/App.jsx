import { useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import NavBar from './components/NavBar.jsx'
import Footer from './components/Footer.jsx'
import CookieConsentBanner from './components/CookieConsentBanner.jsx'
import HelpWidget from './components/HelpWidget.jsx'
import ScrollManager from './components/ScrollManager.jsx'
import { HelpWidgetProvider } from './context/HelpWidgetContext.jsx'
import { ChatNudgeProvider } from './context/ChatNudgeContext.jsx'
import { FeaturedProvider } from './context/FeaturedContext.jsx'
import { useCookieConsent } from './context/CookieConsentContext.jsx'
import { loadGoogleAnalytics } from './core/analytics.js'
import ProductsPage from './pages/ProductsPage.jsx'
import ProductDetailPage from './pages/ProductDetailPage.jsx'
import FeaturedPage from './pages/FeaturedPage.jsx'
import CartPage from './pages/CartPage.jsx'
import AreaSelectPage from './pages/AreaSelectPage.jsx'
import OrderConfirmationPage from './pages/OrderConfirmationPage.jsx'
import MyOrdersPage from './pages/MyOrdersPage.jsx'
import ShippingPage from './pages/ShippingPage.jsx'
import HelpPage from './pages/HelpPage.jsx'
import HeavyOrderPage from './pages/HeavyOrderPage.jsx'
import AboutPage from './pages/AboutPage.jsx'
import TrackOrderPage from './pages/TrackOrderPage.jsx'

// "remove the splash screen it doesnt feel so professional" - the site
// opens straight onto its pages (the mobile app keeps its own splash).
export default function App() {
  const { hasConsent } = useCookieConsent()
  const analyticsAllowed = hasConsent('analytics')

  // Only ever loads once analytics consent is actually granted - if it's
  // granted later (via "Customize" after an initial reject), this effect
  // re-fires and loads it then instead of requiring a page reload.
  useEffect(() => {
    if (analyticsAllowed) loadGoogleAnalytics()
  }, [analyticsAllowed])

  return (
    <HelpWidgetProvider>
      <ChatNudgeProvider>
      <FeaturedProvider>
      <ScrollManager />
      <div className="app-shell">
        <NavBar />
        <main className="app-main">
          <Routes>
            <Route path="/" element={<ProductsPage />} />
            <Route path="/products/:productId" element={<ProductDetailPage />} />
            <Route path="/featured" element={<FeaturedPage />} />
            {/* The old address, so links shared before the rename still work. */}
            <Route path="/marketing" element={<Navigate to="/featured" replace />} />
            <Route path="/cart" element={<CartPage />} />
            <Route path="/cart/area" element={<AreaSelectPage />} />
            <Route path="/orders/:orderRef" element={<OrderConfirmationPage />} />
            <Route path="/my-orders" element={<MyOrdersPage />} />
            <Route path="/track" element={<TrackOrderPage />} />
            <Route path="/shipping" element={<ShippingPage />} />
            <Route path="/help" element={<HelpPage />} />
            <Route path="/heavy-order" element={<HeavyOrderPage />} />
            <Route path="/about" element={<AboutPage />} />
          </Routes>
        </main>
        <Footer />
        <HelpWidget />
        <CookieConsentBanner />
      </div>
      </FeaturedProvider>
      </ChatNudgeProvider>
    </HelpWidgetProvider>
  )
}
