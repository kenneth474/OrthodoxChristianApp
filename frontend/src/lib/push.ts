/*
  push.ts: turning notifications on and off on this device (the Push API).

  1. When the app starts, it registers the service worker (public/sw.js),
     the script that shows notifications.
  2. "Turn on notifications" asks the browser for a "push subscription"
     tied to our server's public key (VapidKeys.java). The browser first
     asks the person for permission. The subscription is an address
     (endpoint) plus keys, which we send to our server.
  3. At the chosen time, the server sends an encrypted message to that
     address; the browser wakes the service worker, which shows it.
*/
import { api } from '../api'

/** Can this device get notifications from the app? */
export type PushSupport = 'ok' | 'ios-browser' | 'unsupported'

export function pushSupport(): PushSupport {
  if ('serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window) return 'ok'
  // On iPhone and iPad, notifications only work in the app added to the
  // Home Screen, not in Safari itself.
  if (isIos() && !isStandalone()) return 'ios-browser'
  return 'unsupported'
}

function isIos(): boolean {
  // (iPads say they're Macs, but Macs have no touch screen.)
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

function isStandalone(): boolean {
  return (
    window.matchMedia?.('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

/** Registers the service worker, once, when the app starts. */
export function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return
  navigator.serviceWorker.register('/sw.js').catch(() => {
    // e.g. some private-browsing modes: notifications just won't be offered
  })
}

/**
 * Turns notifications off for this device, if they're on: the server
 * forgets it, and the browser drops the subscription. Used by "Turn off"
 * and when signing out (the next person on this device shouldn't get your
 * name-day greeting).
 */
export async function unsubscribeThisDevice(): Promise<void> {
  if (pushSupport() !== 'ok') return
  const registration = await navigator.serviceWorker.getRegistration()
  const subscription = await registration?.pushManager.getSubscription()
  if (!subscription) return
  await api('POST', '/api/notifications/devices/remove', { endpoint: subscription.endpoint })
  await subscription.unsubscribe()
}

/** The browser's time zone, e.g. "America/Chicago", so notifications come at local time. */
export function timeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

/**
 * The server's public key, in the form browsers want (bytes). Base64url is
 * base64 with - and _ instead of + and /, and no = at the end.
 */
export function fromBase64Url(text: string): Uint8Array<ArrayBuffer> {
  const base64 = text.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(text.length / 4) * 4, '=')
  const binary = atob(base64)
  const bytes = new Uint8Array(new ArrayBuffer(binary.length))
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}
