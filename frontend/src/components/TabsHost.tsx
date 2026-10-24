/*
  TabsHost: the five tabs, each kept exactly where you left it.

  HOW IT WORKS
  Normally a router shows ONE screen: the one for the current address. Here
  every tab you've opened stays alive, each showing ITS OWN last address:
      Home    -> /?date=2026-12-25
      Bible   -> /bible/PSA/50        (the current address: visible)
      Prayers -> /prayers/morning
  Only the current tab is visible. The others are wrapped in React's
  <Activity mode="hidden">: hidden and paused, but with everything kept: the
  page, what you'd opened, what had loaded. Coming back shows it instantly.
  <Routes location={...}> is what lets each tab draw its own address instead
  of the browser's.

  Also here:
  - Scroll positions: remembered for each address, and put back when you
    return to it (so each tab reopens where you'd scrolled to).
  - Pull to refresh (PullToRefresh.tsx): refreshes just the current tab.
  - TabsContext (tabs-context.ts): tells the tab bar where each tab was, so tapping a tab
    takes you back to its last page, and tapping the current tab again goes
    to its first page.
*/
import { Activity, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate, type Location } from 'react-router'
import { useAccount } from '../account-context'
import { forgetScope, RefreshContext } from '../lib/data'
import { TABS, tabFor, type TabId } from '../lib/tabs'
import { TabsContext } from '../tabs-context'
import { BiblePage } from '../pages/BiblePage'
import { ChurchPage } from '../pages/ChurchPage'
import { HomePage } from '../pages/HomePage'
import { PrayersPage } from '../pages/PrayersPage'
import { ProfileTab } from '../pages/ProfileTab'
import { PullToRefresh } from './PullToRefresh'
import { TabBar } from './TabBar'

/** The screens of each tab (the addresses are explained in App.tsx). */
function tabRoutes(tab: TabId): ReactNode {
  switch (tab) {
    case 'home':
      return <Route path="/" element={<HomePage />} />
    case 'bible':
      return (
        <>
          <Route path="/bible" element={<BiblePage />} />
          <Route path="/bible/:book" element={<BiblePage />} />
          <Route path="/bible/:book/:chapter" element={<BiblePage />} />
          <Route path="/bible/*" element={<Navigate to="/bible" replace />} />
        </>
      )
    case 'prayers':
      return (
        <>
          <Route path="/prayers" element={<PrayersPage />} />
          <Route path="/prayers/:section" element={<PrayersPage />} />
          <Route path="/prayers/*" element={<Navigate to="/prayers" replace />} />
        </>
      )
    case 'church':
      return (
        <>
          <Route path="/church" element={<ChurchPage />} />
          <Route path="/church/ministries" element={<ChurchPage />} />
          <Route path="/church/dioceses/:diocese" element={<ChurchPage />} />
          <Route path="/church/*" element={<Navigate to="/church" replace />} />
        </>
      )
    case 'profile':
      return (
        <>
          {/* Both show your profile when signed in, the sign-in form when not (ProfileTab). */}
          <Route path="/profile" element={<ProfileTab />} />
          <Route path="/sign-in" element={<ProfileTab />} />
          <Route path="/profile/members" element={<ProfileTab page="members" />} />
          <Route path="/profile/*" element={<Navigate to="/profile" replace />} />
        </>
      )
  }
}

export function TabsHost() {
  const location = useLocation()
  const navigate = useNavigate()
  const { account, refresh: refreshAccount } = useAccount()
  const active = tabFor(location.pathname)

  // Each opened tab's last address. Updated while drawing (React allows a
  // component to update its own state this way), so the tab shows the new
  // address straight away.
  const [locations, setLocations] = useState<Partial<Record<TabId, Location>>>({})
  if (active && locations[active] !== location) {
    setLocations({ ...locations, [active]: location })
  }
  const shown: Partial<Record<TabId, Location>> = active ? { ...locations, [active]: location } : locations

  // Each tab's refresh count: pulling to refresh adds one, which starts the
  // tab afresh (its key changes) and makes its data load again (new scope).
  const [generations, setGenerations] = useState<Record<TabId, number>>({
    home: 0, bible: 0, prayers: 0, church: 0, profile: 0,
  })

  const refresh = useCallback(() => {
    if (!active) return
    forgetScope(`${active}:${generations[active]}`)
    if (active === 'profile') void refreshAccount()
    setGenerations((g) => ({ ...g, [active]: g[active] + 1 }))
  }, [active, generations, refreshAccount])

  const pathFor = useCallback(
    (tab: TabId) => {
      const last = locations[tab] ?? (tab === active ? location : undefined)
      // The Profile tab: /sign-in when you're not signed in. When you are,
      // its last page (the Members page, say), but never /sign-in.
      if (tab === 'profile') {
        if (!account) return '/sign-in'
        return last && last.pathname.startsWith('/profile/') ? last.pathname : '/profile'
      }
      if (last) return last.pathname + last.search
      return TABS.find((t) => t.id === tab)!.root
    },
    [locations, active, location, account],
  )

  // ----- Scroll positions, remembered per address -----
  const pageKey = location.pathname + location.search
  const scrollPositions = useRef(new Map<string, number>())
  const currentPage = useRef(pageKey)

  // Put back the scroll position for the address we've just arrived at
  // (top of the page if it's new). useLayoutEffect runs before the screen is
  // painted, so the page never flickers at the wrong position.
  useLayoutEffect(() => {
    currentPage.current = pageKey
    window.scrollTo(0, scrollPositions.current.get(pageKey) ?? 0)
  }, [pageKey])

  // Remember the position as you scroll. ("manual": the browser shouldn't
  // also try to put scroll positions back on Back/Forward; we do it here.)
  useEffect(() => {
    window.history.scrollRestoration = 'manual'
    const onScroll = () => scrollPositions.current.set(currentPage.current, window.scrollY)
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  if (!active) return <Navigate to="/" replace /> // an address no tab owns

  return (
    <TabsContext.Provider value={{ active, pathFor }}>
      <PullToRefresh onRefresh={refresh} />
      {TABS.filter((tab) => shown[tab.id]).map((tab) => (
        <Activity key={tab.id} mode={tab.id === active ? 'visible' : 'hidden'}>
          <RefreshContext.Provider value={`${tab.id}:${generations[tab.id]}`}>
            <div className="tab-panel" key={generations[tab.id]}>
              <Routes location={shown[tab.id]}>{tabRoutes(tab.id)}</Routes>
            </div>
          </RefreshContext.Provider>
        </Activity>
      ))}
      <TabBar onReselect={(tab) => {
        // Tapping the tab you're on: back to its first page, or to the top.
        const root = tab === 'profile' && !account ? '/sign-in' : TABS.find((t) => t.id === tab)!.root
        if (location.pathname === root && !location.search) window.scrollTo({ top: 0, behavior: 'smooth' })
        else navigate(root)
      }} />
    </TabsContext.Provider>
  )
}
