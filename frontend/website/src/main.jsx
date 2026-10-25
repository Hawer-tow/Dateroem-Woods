import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { CartProvider } from './context/CartContext.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { ThemeProvider } from './context/ThemeContext.jsx'
import { CookieConsentProvider } from './context/CookieConsentContext.jsx'
import { FOREST_LOOK } from './core/look.js'
import './styles/index.css'
import './styles/forest.css'

// The forest look's styles all wait for this mark (styles/forest.css).
if (FOREST_LOOK) document.documentElement.dataset.look = 'forest'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <CookieConsentProvider>
        <AuthProvider>
          <ThemeProvider>
            <CartProvider>
              <App />
            </CartProvider>
          </ThemeProvider>
        </AuthProvider>
      </CookieConsentProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
