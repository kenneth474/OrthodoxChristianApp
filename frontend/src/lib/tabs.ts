/*
  tabs.ts: the five tabs, and which tab an address belongs to.

  Used by TabsHost (which keeps every tab's screen alive) and TabBar.
*/
export type TabId = 'home' | 'bible' | 'prayers' | 'church' | 'profile'

export interface TabInfo {
  id: TabId
  label: string
  root: string // the tab's first page: tapping the current tab again goes back here
}

export const TABS: TabInfo[] = [
  { id: 'home', label: 'Home', root: '/' },
  { id: 'bible', label: 'Bible', root: '/bible' },
  { id: 'prayers', label: 'Prayers', root: '/prayers' },
  { id: 'church', label: 'Church', root: '/church' },
  { id: 'profile', label: 'Profile', root: '/profile' },
]

/** "/bible/PSA/50" -> "bible". null for an address no tab owns. */
export function tabFor(pathname: string): TabId | null {
  if (pathname === '/') return 'home'
  for (const id of ['bible', 'prayers', 'church'] as const) {
    if (pathname === `/${id}` || pathname.startsWith(`/${id}/`)) return id
  }
  if (pathname === '/profile' || pathname.startsWith('/profile/') || pathname === '/sign-in') return 'profile'
  return null
}
