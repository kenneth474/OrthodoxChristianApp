/*
  types.ts: the shapes of the data our backend sends.

  WHAT IS A TYPE?
  TypeScript lets us describe what a piece of data looks like. Each
  "interface" below mirrors a Java record in the backend, e.g. LiturgicalDay
  here = LiturgicalDay.java there. If we then misspell a field (day.titel) or
  use a number as text, VS Code underlines it in red before we even run the app.

  "string | null" means "text, or nothing". A list of words written as
  'SMALL' | 'NORMAL' | 'LARGE' means "exactly one of these words".
*/

// ----- The day: GET /api/day/{year}/{month}/{day} (LiturgicalDay.java) -----

export interface Verse {
  chapter: number
  number: number
  text: string
  paragraphStart: boolean
}

export interface Reading {
  service: string // "Epistle" or "Gospel"
  book: string // e.g. "St. Paul's Letter to the Galatians"
  chapters: string // e.g. "1:11-19"
  reference: string // e.g. "Galatians 1:11-19"
  note: string // e.g. "St James"; empty for the ordinary daily reading
  verses: Verse[]
}

export interface Life {
  title: string
  html: string // the story as HTML; always cleaned with sanitizeHtml before showing
}

export interface Fasting {
  rule: string // e.g. "WINE_AND_OIL" (FastingRule.java)
  label: string // e.g. "Wine and oil allowed"
  season: string
  abstainFrom: string[]
}

export interface Feast {
  level: number
  rank: string
  names: string[]
}

export interface LiturgicalDay {
  date: string // "2026-10-23"
  title: string
  subtitles: string[]
  feast: Feast
  fasting: Fasting
  tone: number
  saints: string[]
  readings: Reading[]
  lives: Life[]
}

// ----- Accounts: /api/account and /api/auth (AccountView.java, AuthController.java) -----

export type TextSize = 'SMALL' | 'NORMAL' | 'LARGE'
export type Theme = 'SYSTEM' | 'LIGHT' | 'DARK'
/** The app's five looks (AppStyle.java; see lib/styles.ts). */
export type AppStyle = 'ILLUMINATED' | 'MOSAIC' | 'VIGIL' | 'ATHOS' | 'SEASONS'

export interface Account {
  email: string
  displayName: string
  phoneNumber: string | null // e.g. "+12145550123"
  hasPassword: boolean
  googleLinked: boolean
  patronSaint: string | null
  nameDay: string | null // your patron saint's feast day, "MM-DD"
  homeParish: string | null
  photoVersion: number | null // changes when the photo does; null = no photo
  textSize: TextSize
  theme: Theme
  style: AppStyle
  admin: boolean // may open the Members page (admin/Admins.java)
}

/** The reply to signing in or creating an account. */
export interface SignInResponse {
  status: 'SIGNED_IN'
  account: Account
}

/** What the server offers (AuthController.SignInOptions). */
export interface SignInOptions {
  google: boolean // the "Continue with Google" button works
}

// ----- The Members page: /api/admin/members (MembersController.java) -----

export interface Member {
  name: string
  email: string
  phoneNumber: string | null
  password: boolean // can sign in with a password (never the password itself)
  google: boolean // can sign in with Google
  joined: string // when the account was made, e.g. "2026-10-24T14:03:00Z"
}

export interface Members {
  count: number
  members: Member[] // newest first
}

// ----- The Bible: /api/bible (BibleBook.java, BibleChapter.java) -----

export interface BibleBook {
  code: string // e.g. "PSA"
  name: string // e.g. "Psalms"
  section: string // e.g. "Wisdom"
  chapters: number
}

export interface BibleVerse {
  number: string // text, because a few verses are like "29a"
  text: string
  paragraphStart: boolean
}

export interface Place {
  code: string
  book: string
  chapter: number
}

export interface BibleChapter {
  code: string
  book: string
  chapter: number
  chapters: number
  verses: BibleVerse[]
  previous: Place | null
  next: Place | null
}

// ----- The prayer book: public/data/prayers.json -----

/** A line is plain text, a prayer said several times, or an instruction. */
export type PrayerLine = string | { text: string; times: number } | { rubric: string }

/** A passage of scripture, e.g. Psalm 50 from verse 3. */
export interface ScriptureRef {
  book: string
  chapter: number
  from?: number // "?" = optional
  to?: number
}

export interface PrayerItem {
  title?: string
  lines?: PrayerLine[]
  scripture?: ScriptureRef
  include?: string // the id of another section to insert here
}

export interface PrayerSection {
  id: string
  title: string
  subtitle?: string
  note?: string
  listed?: boolean // false = only used inside other sections
  items: PrayerItem[]
}

export interface PrayerBook {
  title: string
  about: string
  sections: PrayerSection[]
}
