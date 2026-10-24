/*
  main.tsx: where the React app starts.

  It finds <div id="root"> in index.html and tells React to draw the App
  inside it. BrowserRouter lets the app change the address (/bible,
  /prayers...) and swap screens WITHOUT reloading the page.
  StrictMode is a development helper: it runs some checks twice to catch
  mistakes early (it does nothing in the finished build).
*/
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { App } from './App'
import { apply } from './lib/appearance'
import { registerServiceWorker } from './lib/push'

// The fonts, packed into the app (no requests to Google): EB Garamond for
// reading, Cormorant Garamond for titles, Cinzel's carved capitals for labels.
import '@fontsource/eb-garamond/latin-400.css'
import '@fontsource/eb-garamond/latin-400-italic.css'
import '@fontsource/eb-garamond/latin-500.css'
import '@fontsource/cormorant-garamond/latin-500.css'
import '@fontsource/cormorant-garamond/latin-600.css'
import '@fontsource/cinzel/latin-500.css'
import './styles/theme.css'
import './styles/app.css'

// Style, light/dark, season and text size (index.html did a first pass).
apply()

// The script that shows notifications (lib/push.ts, public/sw.js).
registerServiceWorker()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
