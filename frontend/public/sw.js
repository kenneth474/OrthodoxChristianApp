/*
  sw.js: the app's "service worker", a small script the browser keeps on the
  device and wakes up when a notification arrives, even if the app is closed.

  It only does notifications. It doesn't save pages or make the app work
  offline (it has no "fetch" handler), so it can never show an old copy of
  the app.

  The server sends each notification encrypted (notify/PushSender.java); the
  browser decrypts it and gives it to us here as JSON:
      { "title": "...", "body": "...", "url": "/", "tag": "daily" }
*/

self.addEventListener('push', (event) => {
  let message = {}
  try {
    message = event.data ? event.data.json() : {}
  } catch {
    message = { body: event.data ? event.data.text() : '' }
  }
  // Every notification that arrives must be shown: Safari stops sending
  // them to apps that don't.
  event.waitUntil(
    self.registration.showNotification(message.title || 'Orthodox Daily', {
      body: message.body || '',
      icon: '/icons/icon-192.png',
      tag: message.tag, // a newer notification with the same tag replaces the older one
      data: { url: message.url || '/' },
    }),
  )
})

// Tapping a notification opens the app at its page (or brings the open app forward).
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL((event.notification.data && event.notification.data.url) || '/', self.location.origin).href
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => 'focus' in client)
      if (!open) return self.clients.openWindow(url)
      return open.focus().then((client) => (client && client.navigate ? client.navigate(url).catch(() => {}) : undefined))
    }),
  )
})

// A new version of this file takes over straight away.
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))
