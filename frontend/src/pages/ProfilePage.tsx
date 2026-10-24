/*
  ProfilePage (the Profile tab, "/profile"): your account.

  Profile (photo, name, patron saint and their feast day, home parish), how
  the app looks, your phone number, notifications, how you sign in, and
  signing out.
  Administrators also get a link to the Members page.
  Talks to AccountController (/api/account/...). Every change sends back the
  updated account, which update() shares with the whole app (so the photo in
  the tab bar changes at once, for example).
*/
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useAccount } from '../account-context'
import { api, ApiError, errorMessage } from '../api'
import { Avatar, Message, type MessageData } from '../components/common'
import { Masthead } from '../components/ornaments'
import { StylePicker } from '../components/StylePicker'
import { forgetAppearance } from '../lib/appearance'
import { phoneText } from '../lib/phone'
import { fromBase64Url, pushSupport, timeZone, unsubscribeThisDevice } from '../lib/push'
import type { Account, AppStyle, NotificationStatus, TextSize, Theme } from '../types'

// Why you're here, from the address: just created an account with your
// email (?welcome=1) or with Google (?welcome=google), or Google has just
// taken over your password sign-in (?google=linked; see AuthService.java).
function arrivalMessage(searchParams: URLSearchParams): MessageData | null {
  if (searchParams.get('welcome')) {
    return { kind: 'info', text: 'Welcome to Orthodox Daily! You can add a photo, your patron saint and your home parish below.' }
  }
  if (searchParams.get('google') === 'linked') {
    return {
      kind: 'info',
      text: 'Your account is now connected to your Google account. From now on, sign in with "Continue with Google".',
    }
  }
  return null
}

export function ProfilePage() {
  const { account, update } = useAccount()
  const [searchParams] = useSearchParams()
  const [message, setMessage] = useState<MessageData | null>(null)
  // Worked out on every draw (not stored), so it's right whenever you arrive.
  const shown = message ?? arrivalMessage(searchParams)
  const navigate = useNavigate()

  if (!account) return null // ProfileTab shows the sign-in page instead

  /**
   * Runs an account change and shows the result. Anything that changes the
   * account sends the new one back, which we share with the whole app.
   * A 401 means the session ended: go to sign-in.
   */
  const run = async (action: () => Promise<Account | void>, success?: string) => {
    try {
      const changed = await action()
      if (changed) update(changed)
      if (!success) return // nothing to show (e.g. a new style): stay where you are
      setMessage({ kind: 'info', text: success })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        update(null) // signed out: the Profile tab then shows the sign-in page
        return
      }
      setMessage({ kind: 'error', text: errorMessage(error), problems: error instanceof ApiError ? error.problems : [] })
    }
    window.scrollTo({ top: 0, behavior: 'smooth' }) // the message is at the top
  }

  // Signing out goes Home; the Profile tab then shows the sign-in page
  // (see ProfileTab). This device stops getting your notifications.
  const signOut = () =>
    run(async () => {
      await unsubscribeThisDevice().catch(() => {})
      await api('POST', '/api/auth/sign-out')
      forgetAppearance() // the next person on this device gets the defaults
      navigate('/', { replace: true })
      update(null)
    })

  // Just signed up with Google: ask for a phone number, as the sign-up form does.
  const askForPhone = searchParams.get('welcome') === 'google' && !account.phoneNumber

  return (
    <div className="screen">
      <Masthead eyebrow={<Avatar account={account} className="avatar masthead-avatar" />} title={account.displayName} subtitle={account.email} />
      <main className="page narrow">
      <Message message={shown} />

      {askForPhone && <PhonePrompt run={run} onDone={() => navigate('/profile', { replace: true })} />}

      {account.admin && (
        <Link className="card card-link continue" to="/profile/members">
          <span className="label">For administrators</span>
          <span className="title">Members</span>
          <span className="go" aria-hidden="true">›</span>
        </Link>
      )}

      {/* key=...: if a different account signs in (or the number is saved
          from the question above), start these forms afresh. */}
      <ProfileCard key={account.email} account={account} run={run} />
      <AppearanceCard account={account} run={run} />
      <PhoneCard key={`phone-${account.email}-${account.phoneNumber}`} account={account} run={run} />
      <NotificationsCard key={`notify-${account.email}`} account={account} run={run} />

      <section className="card">
        <h2>Ways you sign in</h2>
        <ul className="plain">
          {account.hasPassword && <li>✓ Email and password</li>}
          {account.googleLinked && <li>✓ Google</li>}
        </ul>
      </section>

      <button type="button" onClick={signOut}>
        Sign out
      </button>
      <p className="footer">
        <a href="/privacy">Privacy policy</a>
      </p>
      </main>
    </div>
  )
}

type Run = (action: () => Promise<Account | void>, success?: string) => Promise<void>
interface CardProps {
  account: Account
  run: Run
}

function Card({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      <h2>{title}</h2>
      {children}
    </section>
  )
}

// ----- Profile: photo, name, patron saint and feast day, home parish -----

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December']
// February has 29: a few saints are kept on the 29th in leap years.
const DAYS_IN_MONTH = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

function ProfileCard({ account, run }: CardProps) {
  // The fields start with the saved values; typing changes them here until "Save".
  const [displayName, setDisplayName] = useState(account.displayName)
  const [patronSaint, setPatronSaint] = useState(account.patronSaint ?? '')
  // The feast day: a month and a day, no year ("12-06" is saved as 12 and 6; 0 = not chosen).
  const [feastMonth, setFeastMonth] = useState(account.nameDay ? Number(account.nameDay.slice(0, 2)) : 0)
  const [feastDay, setFeastDay] = useState(account.nameDay ? Number(account.nameDay.slice(3)) : 0)
  const [homeParish, setHomeParish] = useState(account.homeParish ?? '')

  // useRef = a handle on a real page element: here, the hidden file picker.
  const filePicker = useRef<HTMLInputElement>(null)

  // A month with fewer days than the chosen day (June 31st): use its last day.
  const chooseMonth = (month: number) => {
    setFeastMonth(month)
    if (month && feastDay > DAYS_IN_MONTH[month - 1]) setFeastDay(DAYS_IN_MONTH[month - 1])
  }
  const days = feastMonth ? DAYS_IN_MONTH[feastMonth - 1] : 31

  const save = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      if (!feastMonth !== !feastDay) throw new Error('Choose both a month and a day for the feast day (or neither).')
      const pad = (n: number) => String(n).padStart(2, '0')
      const nameDay = feastMonth ? `${pad(feastMonth)}-${pad(feastDay)}` : ''
      return api<Account>('PUT', '/api/account/profile', { displayName, patronSaint, nameDay, homeParish })
    }, 'Profile saved.')
  }

  const upload = (file: File | undefined) => {
    if (!file) return
    void run(async () => {
      let square: Blob
      try {
        square = await squarePhoto(file)
      } catch {
        throw new Error('Please choose a JPEG or PNG photo.')
      }
      const form = new FormData()
      form.append('photo', square, 'photo.jpg')
      return api<Account>('POST', '/api/account/photo', form)
    }, 'Photo saved.')
  }

  return (
    <Card title="Profile">
      <div className="photo-row">
        <Avatar account={account} />
        <div className="buttons">
          <button type="button" onClick={() => filePicker.current?.click()}>
            Change photo
          </button>
          {account.photoVersion && (
            <button
              type="button"
              className="link"
              onClick={() => run(() => api<Account>('DELETE', '/api/account/photo'), 'Photo removed.')}
            >
              Remove photo
            </button>
          )}
          <input
            ref={filePicker}
            type="file"
            accept="image/jpeg,image/png"
            hidden
            onChange={(e) => {
              upload(e.target.files?.[0])
              e.target.value = '' // so choosing the same file again still counts
            }}
          />
        </div>
      </div>
      <form onSubmit={save}>
        <label>
          Name
          <input maxLength={60} required autoComplete="name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </label>
        {/* The saint and their feast day belong together: on that day the
            Home screen wishes you a happy name day. */}
        <fieldset className="patron">
          <legend>
            Patron saint <span className="optional">(optional)</span>
          </legend>
          <input
            maxLength={100}
            placeholder="e.g. St. Nicholas the Wonderworker"
            aria-label="Patron saint"
            value={patronSaint}
            onChange={(e) => setPatronSaint(e.target.value)}
          />
          <div className="feast-day">
            <span>Feast day</span>
            <select aria-label="Feast day: month" value={feastMonth} onChange={(e) => chooseMonth(Number(e.target.value))}>
              <option value={0}>Month</option>
              {MONTHS.map((name, i) => (
                <option key={name} value={i + 1}>
                  {name}
                </option>
              ))}
            </select>
            <select aria-label="Feast day: day" value={feastDay} onChange={(e) => setFeastDay(Number(e.target.value))}>
              <option value={0}>Day</option>
              {Array.from({ length: days }, (_, i) => (
                <option key={i + 1} value={i + 1}>
                  {i + 1}
                </option>
              ))}
            </select>
          </div>
        </fieldset>
        <label>
          Home parish <span className="optional">(optional)</span>
          <input
            maxLength={120}
            placeholder="e.g. Saints Constantine and Helen, Carrollton"
            value={homeParish}
            onChange={(e) => setHomeParish(e.target.value)}
          />
        </label>
        <button type="submit">Save profile</button>
      </form>
    </Card>
  )
}

/**
 * Crops a photo to the middle square and shrinks it to 512 x 512, using a
 * <canvas> (a picture you can draw on in code). This also turns phone photos
 * the right way up and makes the upload tiny. The server still checks and
 * re-saves it (ProfilePhotoService.java).
 */
async function squarePhoto(file: File): Promise<Blob> {
  const image = await createImageBitmap(file) // fails if it isn't a picture
  const side = Math.min(image.width, image.height)
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 512
  canvas
    .getContext('2d')!
    .drawImage(image, (image.width - side) / 2, (image.height - side) / 2, side, side, 0, 0, 512, 512)
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('no image'))), 'image/jpeg', 0.9),
  )
}

// ----- Appearance: saved the moment you pick an option -----

const TEXT_SIZES: { size: TextSize; label: string }[] = [
  { size: 'SMALL', label: 'Small' },
  { size: 'NORMAL', label: 'Normal' },
  { size: 'LARGE', label: 'Large' },
]

function AppearanceCard({ account, run }: CardProps) {
  const save = (textSize: TextSize, theme: Theme, style: AppStyle) =>
    run(() => api<Account>('PUT', '/api/account/appearance', { textSize, theme, style }))

  return (
    <Card title="Appearance">
      <fieldset className="choices">
        <legend>Style</legend>
        <StylePicker value={account.style} onChange={(style) => save(account.textSize, account.theme, style)} />
      </fieldset>
      <fieldset className="choices">
        <legend>Text size</legend>
        <div className="row">
          {TEXT_SIZES.map(({ size, label }) => (
            <label key={size}>
              <input
                type="radio"
                name="textSize"
                checked={account.textSize === size}
                onChange={() => save(size, account.theme, account.style)}
              />{' '}
              {label}
            </label>
          ))}
        </div>
      </fieldset>
      <fieldset className="choices">
        <legend>Light or dark</legend>
        {account.style === 'VIGIL' && <p className="hint">Candlelit vigil is always dark.</p>}
        <div className="row">
          {(['SYSTEM', 'LIGHT', 'DARK'] as Theme[]).map((theme) => (
            <label key={theme}>
              <input
                type="radio"
                name="theme"
                checked={account.theme === theme}
                onChange={() => save(account.textSize, theme, account.style)}
              />{' '}
              {theme === 'SYSTEM' ? 'Match my device' : theme === 'LIGHT' ? 'Light' : 'Dark'}
            </label>
          ))}
        </div>
      </fieldset>
    </Card>
  )
}

// ----- Phone number (optional) -----

function PhoneCard({ account, run }: CardProps) {
  const [phone, setPhone] = useState(account.phoneNumber ? phoneText(account.phoneNumber) : '')

  const save = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      const updated = await api<Account>('PUT', '/api/account/phone', { phone })
      setPhone(updated.phoneNumber ? phoneText(updated.phoneNumber) : '') // the tidied-up number
      return updated
    }, phone.trim() ? 'Phone number saved.' : 'Phone number removed.')
  }

  return (
    <Card title={<>Phone <span className="optional">(optional)</span></>}>
      <form className="inline" onSubmit={save}>
        <input
          type="tel"
          autoComplete="tel"
          placeholder="(214) 555-0123"
          aria-label="Phone number"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        <button type="submit">Save</button>
      </form>
      <p className="hint">To remove your number, clear the box and save.</p>
    </Card>
  )
}

// ----- Notifications (lib/push.ts; NotificationService.java on the server) -----

const BLOCKED_HELP =
  'Notifications are blocked for Orthodox Daily on this device. On iPhone: Settings, then Notifications, then ' +
  'Orthodox Daily, and turn on Allow Notifications. In a browser: the icon left of the address, then Notifications.'

// "12-06" -> "December 6"
function feastDayText(nameDay: string): string {
  return `${MONTHS[Number(nameDay.slice(0, 2)) - 1]} ${Number(nameDay.slice(3))}`
}

function NotificationsCard({ account, run }: CardProps) {
  const support = pushSupport()
  // This device: still finding out, or notifications on, off, or blocked.
  const [device, setDevice] = useState<'checking' | 'on' | 'off' | 'blocked'>('checking')
  const [registration, setRegistration] = useState<ServiceWorkerRegistration | null>(null)
  const [publicKey, setPublicKey] = useState<string | null>(null)
  const [time, setTime] = useState(account.notifyTime)

  // Are notifications already on here? If so, remind the server of this
  // device, in case it forgot it (a new database, say). The server's key and
  // the service worker are fetched now, because turning on must start
  // straight from the tap (Safari's rule), with nothing to wait for.
  useEffect(() => {
    if (support !== 'ok') return
    let cancelled = false
    api<NotificationStatus>('GET', '/api/notifications')
      .then((status) => !cancelled && setPublicKey(status.publicKey))
      .catch(() => {})
    void navigator.serviceWorker.ready.then(async (ready) => {
      const subscription = await ready.pushManager.getSubscription()
      if (cancelled) return
      setRegistration(ready)
      if (subscription) {
        setDevice('on')
        void api('POST', '/api/notifications/devices', subscription.toJSON()).catch(() => {})
      } else {
        setDevice(Notification.permission === 'denied' ? 'blocked' : 'off')
      }
    })
    // If the service worker never gets ready, still offer the button.
    const timer = window.setTimeout(() => setDevice((now) => (now === 'checking' ? 'off' : now)), 5000)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [support])

  // The settings, with some changed. The time zone comes from this device.
  const settings = (changes: { daily?: boolean; nameDay?: boolean; time?: string }) => ({
    daily: account.notifyDaily,
    nameDay: account.notifyNameDay,
    time: account.notifyTime,
    timeZone: timeZone(),
    ...changes,
  })
  const save = (changes: { daily?: boolean; nameDay?: boolean; time?: string }, success: string) =>
    run(() => api<Account>('PUT', '/api/notifications/settings', settings(changes)), success)

  const turnOn = () =>
    run(async () => {
      if (!registration || !publicKey) throw new Error('Notifications are still getting ready. Please try again in a moment.')
      let subscription: PushSubscription
      try {
        // The browser asks the person first ("Allow notifications?").
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: fromBase64Url(publicKey),
        })
      } catch {
        if (Notification.permission === 'denied') {
          setDevice('blocked')
          throw new Error(BLOCKED_HELP)
        }
        throw new Error("Notifications couldn't be turned on. Please try again.")
      }
      await api('POST', '/api/notifications/devices', subscription.toJSON())
      setDevice('on')
      return api<Account>('PUT', '/api/notifications/settings', settings({})) // saves the time zone
    }, 'Notifications are on for this device.')

  const turnOff = () =>
    run(async () => {
      await unsubscribeThisDevice()
      setDevice('off')
    }, 'Notifications are off for this device.')

  const test = () =>
    run(async () => {
      const result = await api<{ sent: number }>('POST', '/api/notifications/test')
      if (result.sent === 0) throw new Error("The test couldn't be sent. Try turning notifications off and on again.")
    }, 'Test notification sent. It should arrive in a few seconds.')

  const nameDayReady = !!account.patronSaint && !!account.nameDay

  return (
    <Card title="Notifications">
      {support === 'ios-browser' && (
        <p className="hint">
          To get notifications on iPhone, add Orthodox Daily to your Home Screen (tap Share, then Add to Home Screen)
          and open it from there.
        </p>
      )}
      {support === 'unsupported' && <p className="hint">This browser can't show notifications from Orthodox Daily.</p>}
      {support === 'ok' && device === 'on' && (
        <div className="notify-device">
          <p>
            <strong>On</strong> for this device.
          </p>
          <div className="button-row">
            <button type="button" onClick={test}>
              Send a test notification
            </button>
            <button type="button" className="link" onClick={turnOff}>
              Turn off
            </button>
          </div>
        </div>
      )}
      {support === 'ok' && (device === 'off' || device === 'checking') && (
        <div className="notify-device">
          <button type="button" className="primary-inline" disabled={device === 'checking'} onClick={turnOn}>
            Turn on notifications
          </button>
        </div>
      )}
      {support === 'ok' && device === 'blocked' && <p className="hint">{BLOCKED_HELP}</p>}

      <fieldset className="choices">
        <legend>What you'll get</legend>
        <label className="check">
          <input
            type="checkbox"
            checked={account.notifyDaily}
            onChange={(e) => save({ daily: e.target.checked }, e.target.checked ? 'Daily notification on.' : 'Daily notification off.')}
          />
          <span>Every day: the feast or saint, the fast and the readings</span>
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={account.notifyNameDay}
            onChange={(e) =>
              save({ nameDay: e.target.checked }, e.target.checked ? 'Name day greeting on.' : 'Name day greeting off.')
            }
          />
          <span>
            A greeting on your name day{nameDayReady && account.nameDay ? ` (${feastDayText(account.nameDay)})` : ''}
          </span>
        </label>
        {!nameDayReady && account.notifyNameDay && (
          <p className="hint">Add your patron saint and their feast day under Profile for this one.</p>
        )}
      </fieldset>
      <form
        className="inline notify-time"
        onSubmit={(e) => {
          e.preventDefault()
          void save({ time }, 'Notification time saved.')
        }}
      >
        <label htmlFor="notify-time">Time</label>
        <input id="notify-time" type="time" step={300} required value={time} onChange={(e) => setTime(e.target.value)} />
        {time !== account.notifyTime && <button type="submit">Save</button>}
      </form>
    </Card>
  )
}

/** Shown once, after signing up with Google: the sign-up form's optional phone question. */
function PhonePrompt({ run, onDone }: { run: Run; onDone: () => void }) {
  const [phone, setPhone] = useState('')
  return (
    <Card title="Your phone number">
      <p>Would you like to add your phone number? It's optional, and you can add it later in Phone below.</p>
      <form
        className="inline"
        onSubmit={(e) => {
          e.preventDefault()
          void run(async () => {
            const updated = await api<Account>('PUT', '/api/account/phone', { phone })
            onDone()
            return updated
          }, 'Phone number saved.')
        }}
      >
        <input
          type="tel"
          autoComplete="tel"
          placeholder="(214) 555-0123"
          aria-label="Phone number"
          required
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        <button type="submit">Save</button>
      </form>
      <button type="button" className="link" onClick={onDone}>
        Not now
      </button>
    </Card>
  )
}
