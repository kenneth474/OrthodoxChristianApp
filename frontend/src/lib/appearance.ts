/*
  appearance.ts: applies your text size, light/dark theme and style.

  They're set as attributes on the <html> tag, which styles/theme.css reads:
      data-style="illuminated" | "mosaic" | "vigil" | "athos" | "seasons"
      data-mode="light" | "dark"     (worked out from your theme choice:
                                      "Match my device" follows the device;
                                      Candlelit vigil is always dark)
      data-season="lent" | ...       (for the Liturgical seasons style)
      data-text-size="small" | "normal" | "large"

  When you're signed in, your choices are saved to your account. A copy is
  also kept in this browser (localStorage), so index.html can apply it
  before the page is drawn, and so the style you pick while creating an
  account shows straight away.
*/
import { useSyncExternalStore } from 'react'
import type { Account, AppStyle, TextSize, Theme } from '../types'
import { toIso } from './dates'
import { liturgicalSeason } from './seasons'
import { DEFAULT_STYLE, isAppStyle } from './styles'

const KEY = 'orthodoxDaily.appearance'

interface Settings {
  theme?: Theme
  textSize?: TextSize
  style?: AppStyle
  season?: string // remembered so index.html can color the page before the app starts
}

let current: Settings = load()
const listeners = new Set<() => void>()

function load(): Settings {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? 'null') ?? {}
  } catch {
    return {} // storage can be switched off (e.g. private browsing); the app still works
  }
}

function save(settings: Settings) {
  current = settings
  try {
    localStorage.setItem(KEY, JSON.stringify(settings))
  } catch {
    // nothing to do: the settings still apply until the page is closed
  }
  apply()
  for (const listener of listeners) listener()
}

const darkQuery = typeof window !== 'undefined' ? window.matchMedia?.('(prefers-color-scheme: dark)') : undefined

/** Sets the <html> attributes from the current settings. */
export function apply() {
  const root = document.documentElement
  const theme = current.theme ?? 'SYSTEM'
  // Candlelit vigil is a night style: always dark, whatever the theme says.
  const dark = currentStyle() === 'VIGIL' || theme === 'DARK' || (theme === 'SYSTEM' && !!darkQuery?.matches)
  const season = liturgicalSeason(toIso(new Date())).id
  if (current.season !== season) {
    current = { ...current, season }
    try {
      localStorage.setItem(KEY, JSON.stringify(current))
    } catch {
      // fine
    }
  }
  root.dataset.theme = theme.toLowerCase()
  root.dataset.mode = dark ? 'dark' : 'light'
  root.dataset.style = currentStyle().toLowerCase()
  root.dataset.season = season
  root.dataset.textSize = (current.textSize ?? 'NORMAL').toLowerCase()
  // The color of the phone's status bar and browser bar.
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', getComputedStyle(root).getPropertyValue('--bar').trim() || '#f3ecdd')
}

// When "Match my device" is chosen, follow the device switching light/dark.
darkQuery?.addEventListener?.('change', apply)

/** The style in use: yours, or the one picked on this device, or the default. */
export function currentStyle(): AppStyle {
  return isAppStyle(current.style) ? current.style : DEFAULT_STYLE
}

/** For components that change with the style (e.g. the season label on Home). */
export function useStyle(): AppStyle {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    currentStyle,
  )
}

/** Apply the signed-in account's settings, and remember them in this browser. */
export function rememberAppearance(account: Account) {
  save({ theme: account.theme, textSize: account.textSize, style: account.style })
}

/** Try a style on this device (e.g. while creating an account). */
export function previewStyle(style: AppStyle) {
  save({ ...current, style })
}

/** On sign-out, so the next person using this browser gets the defaults. */
export function forgetAppearance() {
  save({})
}
