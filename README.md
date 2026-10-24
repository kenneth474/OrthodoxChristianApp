# Orthodox Daily: An Antiochian Orthodox Companion App

[![Build and test](https://github.com/kenneth474/OrthodoxChristianApp/actions/workflows/ci.yml/badge.svg)](https://github.com/kenneth474/OrthodoxChristianApp/actions/workflows/ci.yml)

A cross-platform app for Antiochian Orthodox Christians, inspired by the
features of [antiochian.org](https://www.antiochian.org): each day's **fasting
rule, scripture readings, saints and their lives**, a **parish finder** for
churches near you, **user accounts**, **church news**, **prayers** and more.
One codebase is planned to run on the web, Windows, macOS, Linux, Android and iOS.

> **Status:** early development (v0.3). The liturgical calendar works for any
> date, with full scripture text, saints' lives and a fasting rule engine.
> Accounts work too: Google sign-in, or email and password with strong
> password rules, an optional phone number, and you stay signed in until you
> sign out. See the [Roadmap](#roadmap) below.
>
> **Try it: [orthodox-daily.onrender.com](https://orthodox-daily.onrender.com)**
> (on Render's free plan, so the first visit after a quiet spell takes about
> a minute while it wakes up; see [Putting the App Online](#putting-the-app-online)).
>
> This is an independent project, not an official app of the Antiochian
> Orthodox Christian Archdiocese of North America.

---

## Features

| Feature | What it does | Status |
|---|---|---|
| **Daily liturgical calendar** | Feast or saint, week title, tone and commemorations for any date | ✅ Working |
| **Scripture readings** | The day's Epistle and Gospel with full verse text, matching antiochian.org | ✅ Working |
| **Lives of the saints** | The story of each saint or feast for the day | ✅ Working |
| **Listen** | Readings and saints' lives read aloud with the device's own voice (no online AI voices); pause, speed, and a choice of three American voices (David, Mark and Zira on Windows; on iPhone Samantha and the best two others) | ✅ Working |
| **Fasting rule engine** | Maps each day to a clear `FastingRule` (strict, wine only, wine & oil, fish, meat fast, fast-free) | ✅ Working |
| **Find a parish near me** | Search churches by your location, city or ZIP, with distance and directions | 🔜 Next |
| **Accounts** | Sign up and sign in with email and password; an email is always needed, a phone number is optional; one account per email | ✅ Working |
| **Stay signed in** | You stay signed in until you choose **Sign out**, even after the server restarts or a long time away | ✅ Working |
| **Strong passwords** | 12+ characters with upper and lower case, a number and a symbol; live checklist; lockout after 5 wrong tries | ✅ Working |
| **Sign in with Google** | One tap: the first time it creates your account (then asks for an optional phone number); if your email already has an account, it opens that one | ✅ Working |
| **Profile** | Profile photo (cropped, resized, location data stripped), patron saint with their feast day (a month and day, and a greeting on your name day), home parish | ✅ Working |
| **Text size & dark mode** | Small / Normal / Large text; match device, light or dark theme; saved to your account | ✅ Working |
| **Members page** | For administrators: how many accounts there are, and each member's name, email, phone and how they sign in (never passwords); download as a spreadsheet | ✅ Working |
| **Privacy policy** | A plain page at `/privacy` saying what the app keeps and why (Google's sign-in screen links to it) | ✅ Working |
| **Five Orthodox styles** | Illuminated manuscript (parchment and red rubrics), Byzantine mosaic (lapis and gold tesserae), Candlelit vigil (always dark, candle glow), Holy Mountain (forest green, stone and terracotta roof tiles) and Liturgical seasons (bright and modern, in the color of the season's vestments); light and dark versions; chosen when creating an account, changed in Profile | ✅ Working |
| **Typikon feast signs** | Each day's rank shown with the service books' signs (red cross in a circle for great feasts, and so on); a red drop capital opens each reading | ✅ Working |
| **Service texts (PDF)** | Each day links to the Archdiocese's Online Liturgical Guide (Orthros, Divine Liturgy, Vespers) | 🔗 Linked; in-app viewer waiting on permission |
| **Home parish & bookmarks** | Choose a home parish, bookmark saints and feasts | 🔜 Planned |
| **Church news** | Archdiocese and parish news articles, managed by admins | 🔜 Planned |
| **Parish events** | Parish admins post events (e.g. *"Vespers moved to 6:30 PM"*) | 🔜 Planned |
| **Tab bar** | Home · Bible · Prayers · Church · Profile at the bottom of every page, like Instagram's; Home is the default. Each tab stays exactly where you left it (page, scroll, open readings); pull down to refresh; tap the current tab to go back to its first page | ✅ Working |
| **Orthodox Bible** | The whole Bible: Septuagint Old Testament (with Tobit, Wisdom, Sirach, Maccabees... and Orthodox Psalm numbering) and King James New Testament; continue reading; listen | ✅ Working |
| **Prayer book** | Beginning, morning and evening prayers, the Creed, meals, the Theotokos, the Jesus Prayer, Communion, the departed, Lent and Pascha; listen | ✅ Working |
| **Church tab** | The Archdiocese: hierarchs, the eight dioceses (with their bishops and states), 20 ministries, and about the Archdiocese, with links to antiochian.org | ✅ Working (parishes come with the parish finder) |
| **Pray today for…** | A different diocese, hierarch or ministry to pray for each day, on Home and in the Church tab | ✅ Working |
| **Calendar caching** | Each day's calendar is kept in the server's memory until midnight (Central), so it loads instantly and spares the free Orthocal service; an older copy is used if Orthocal is down | ✅ Working |
| **Notifications** | Turned on per device in Profile: every day at the time you choose, the feast or saint, the fast and the readings; on your name day, a cheerful greeting. On iPhone they work in the app added to the Home Screen (iOS 16.4 or later) | ✅ Working |
| **Install on any device** | Installable web app (PWA), plus Android and iOS store apps | 🔜 Planned |

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| **Language (backend)** | Java 17+ | Strongly typed, widely used in industry |
| **Backend framework** | Spring Boot 4 (Spring Web MVC) | REST APIs, dependency injection, scheduling, security |
| **HTTP client** | Spring `RestClient` | Calls the external calendar API |
| **JSON** | Jackson | Converts JSON to and from Java objects |
| **Build tool** | Maven (with the Maven Wrapper, `mvnw`) | Downloads libraries, compiles, tests, packages the app |
| **Calendar data** | [Orthocal API](https://orthocal.info/api/) | Free Orthodox calendar API with a Greek/Antiochian tradition setting |
| **Database** | Spring Data JPA with H2 on your computer (stored in `data/`) and PostgreSQL online | User accounts now; parishes, events, news, bookmarks next |
| **Packaging** | Docker (a multi-stage `Dockerfile`) | One container with the React app, the backend and Java, runnable on any host |
| **Hosting** | [Render](https://render.com) (free plan to start), set up by `render.yaml` | Builds and deploys from GitHub after the checks pass; free HTTPS address |
| **Security** | Spring Security: sessions, a signed "stay signed in" cookie (HMAC-SHA256), CSRF protection, BCrypt password hashing | Sign-in, protected account pages |
| **Google sign-in** | OAuth 2.0 / OpenID Connect (Spring Security OAuth2 Client) | Sign in without a new password |
| **Notifications** | Web Push: the Push API and a service worker in the browser; on the server, message encryption (RFC 8291) and VAPID signing (RFC 8292) written with Java's built-in cryptography, and a `@Scheduled` job every minute | Free notifications to iPhones, Android and computers, with no third-party service |
| **Maps & location** *(planned)* | Browser Geolocation API + OpenStreetMap | "Near me" parish search and maps |
| **Cache** | `CalendarCache`: in the server's memory | Each day's calendar data, kept until midnight Central time (one server doesn't need Redis) |
| **Fonts** | EB Garamond, Cormorant Garamond and Cinzel (SIL Open Font License), packed into the app with Fontsource | Book-like reading text and carved-inscription capitals, with no requests to Google |
| **Frontend** | React 19 + TypeScript, built with Vite; React Router for the tabs | One app for every screen; adds to a phone's home screen and opens full screen |
| **Frontend tests** | Vitest (with jsdom) | Tests the helpers: dates, HTML cleaning, reading aloud, prayer book |
| **Mobile apps** *(planned)* | Capacitor | Wraps the React app as native Android and iOS apps |
| **Dev tools** | VS Code, Spring Boot DevTools, Git/GitHub | Editing, auto-restart on save, version control |

---

## Architecture

```
 ┌──────────────────────────┐        ┌──────────────────────────────┐        ┌──────────────────┐
 │  Frontend                │  JSON  │  Backend (Spring Boot)       │  JSON  │  Orthocal API    │
 │  Web / PWA / Android /   │ ─────▶ │                              │ ─────▶ │  orthocal.info   │
 │  iOS (React + Capacitor) │ ◀───── │  • CalendarController        │ ◀───── │  Greek tradition │
 │                          │        │  • CalendarService           │        │  New calendar    │
 │  Today: the React app    │        │  • OrthocalClient            │        └──────────────────┘
 │  (frontend/), on any     │        │  • AuthController, accounts  │  ────▶  Google sign-in
 │  browser or home screen  │        │  • Bible, parishes (planned) │
 └──────────────────────────┘        └──────┬───────────────┬───────┘
                                            │               │
                                     ┌──────▼─────┐   ┌─────▼──────┐
                                     │ H2 locally,│   │ Calendar-  │
                                     │ PostgreSQL │   │ Cache: each│
                                     │ online:    │   │ day, kept  │
                                     │ users, ... │   │ in memory  │
                                     └────────────┘   └────────────┘
```

**The backend is built in layers, each with one job:**

```
CalendarController  ──▶  CalendarService  ──▶  OrthocalClient  ──▶  orthocal.info
 (web requests)          (business rules)      (outside API)
```

**How a request flows:**
1. The browser opens `http://localhost:8080`, which loads the React app
   (`index.html` plus its JavaScript, built from `frontend/`).
2. The Home screen (`frontend/src/pages/HomePage.tsx`) calls our backend, e.g. `/api/day/2026/12/25`.
3. `CalendarController` passes the date to `CalendarService`.
4. `CalendarService` looks in `CalendarCache` first; if the day isn't there
   (or is from before midnight), it asks `OrthocalClient`, which fetches Orthocal's JSON.
   Jackson turns it into an `OrthocalDay` record (a mirror of Orthocal's format).
5. `CalendarService` converts that into our own `LiturgicalDay` record and works
   out the `FastingRule`.
6. `LiturgicalDay` goes back as JSON, and React draws the cards.
7. If anything goes wrong, `ApiExceptionHandler` sends a clear JSON error.

### About the calendar data

- We use Orthocal's **Greek tradition** (`/api/greek/...`). The Antiochian
  Archdiocese follows Greek practice for fasting and commemorations.
- We use the **New calendar** (`/gregorian/`), so fixed feasts such as
  Christmas fall on the civil date.
- Orthocal lists every reading of the day (Vespers, Matins, weekday and
  saints' readings). `ReadingSelector` keeps the ones **antiochian.org** lists:
  the Liturgy's Epistle and Gospel, with a saint's or feast's readings replacing
  the weekday ones (on Sundays the Sunday Gospel stays). Checked against 47 days
  on antiochian.org. `ScriptureBooks` writes references their way, e.g.
  "St. Paul's Letter to the Galatians 1:11-19".
- `fast_level` names the **fasting season** (e.g. Nativity Fast). It is *not* a
  strictness scale. Strictness comes from `fast_exception` (a code from 0 to
  11), which `FastingRule.from(...)` turns into a clear rule.
- Orthocal reflects strict typikon practice. Parishes may relax rules
  pastorally, so the app should always point people to their priest.

### How signing in works

- **Sessions, not tokens.** Signing in stores your account ID in a server-side
  session; the browser keeps only a random session cookie. Every POST/PUT also
  needs a CSRF token, which the pages fetch from `/api/auth/csrf`.
- **Staying signed in** (`StaySignedIn`): sessions live in the server's memory
  and end when it restarts (on the free plan, after every 15 quiet minutes).
  So signing in also sets a long-lasting cookie: your account number, a date,
  and an HMAC-SHA256 signature made with a secret key kept in the database
  (`ServerSecret`). When a request arrives without a session, the cookie signs
  you in again and is renewed for another 400 days. **Sign out** deletes it.
- **Passwords** are checked against `PasswordPolicy` and stored only as BCrypt
  hashes. 5 wrong passwords in a row lock the account for 15 minutes, and a
  wrong email gets the same reply as a wrong password.
- **One account per email**, compared without capital letters; the database's
  email column is also unique.
- **Google:** Spring Security handles the OAuth/OpenID Connect exchange;
  `SocialSignInHandler` then finds or creates the account. Emails typed when
  creating an account aren't checked, so when Google proves who owns an email
  that already has an account, that account's password is removed and it
  opens with Google from then on. Nobody can claim someone else's email in advance.
- **Administrators** (`admin/Admins`): accounts whose email is in the
  `app.admin.emails` setting *and* proven by signing in with Google once.
- **Two-step verification** was built and then taken out on 2026-10-24, until
  after launch; it's in the Git history for when it comes back.

### How notifications work

```
 Profile: "Turn on notifications"
   └─▶ the browser asks permission, then its push service (Apple, Google, Mozilla...)
       gives an address + keys for this device ─▶ saved on our server (PushSubscription)

 Every minute, NotificationScheduler ─▶ NotificationService: whose chosen time is it?
   └─▶ PushSender: message ─JSON─▶ encrypted for that device (WebPushCrypto)
       ─▶ POST to the device's address, signed with our VAPID key (VapidKeys)
       ─▶ the phone's service worker (frontend/public/sw.js) shows it
```

- **Two kinds:** the daily one (feast or saint, fast, readings), if chosen;
  and the name-day greeting, on unless turned off, when the patron saint and
  their feast day are filled in. Both come at the chosen time, in the
  person's own time zone, once a day.
- **Turning off:** per device in Profile (and on sign-out). A device whose
  notifications were turned off in its Settings answers "gone", and is forgotten.
- **Asleep?** The free server sleeps after 15 quiet minutes and can't send
  then, so `.github/workflows/keep-awake.yml` visits it every 10 minutes.
  Anything that came due while it waited still goes out, up to 3 hours late.
- **Encryption** is tested against the worked example in RFC 8291
  (`WebPushCryptoTest`); `PushSenderTest` plays a push service and a phone.

---

## Project Structure

```
OrthodoxChristianApp/
├── pom.xml                          # Maven "recipe": Spring Boot version, libraries, Java version
├── mvnw, mvnw.cmd, .mvn/            # Maven Wrapper: run Maven without installing it
├── secrets.example.properties       # Template for secrets.properties (keys; never committed)
├── README.md                        # This file
├── .gitignore                       # Files Git should not track (build output, secrets, data/)
├── Dockerfile                       # Packs the whole app into a container image (how it runs online)
├── .dockerignore                    # Files the Dockerfile must not copy in (secrets, local builds)
├── render.yaml                      # Render "Blueprint": the online web app and its PostgreSQL database
├── frontend/                        # The React app (all the screens); see frontend/README.md
│   ├── src/pages/                   # Home, Bible, Prayers, Church, Profile, Sign-in
│   ├── src/components/              # TabsHost (keeps every tab alive), tab bar, pull to refresh, ornaments, style picker, player
│   ├── src/lib/                     # Helpers: dates, data loading, styles, Church seasons, daily intention, reading aloud
│   ├── src/styles/                  # theme.css (the five styles' colors and fonts) and app.css (the screens)
│   ├── public/data/prayers.json     # The prayers themselves (edit this to add or fix a prayer)
│   ├── public/data/church.json      # The Archdiocese: hierarchs, dioceses, ministries (edit when they change)
│   ├── public/privacy.html          # The privacy policy (orthodox-daily.onrender.com/privacy)
│   ├── public/sw.js                 # The service worker: shows notifications, even with the app closed
│   └── public/icons/                # The app icon (gold Orthodox cross)
├── .github/
│   ├── workflows/ci.yml             # GitHub Actions: test and build everything on every push, then try the container
│   ├── workflows/keep-awake.yml     # Visits the online app every 10 minutes, so it's awake to send notifications
│   ├── scripts/smoke-test.sh        # Quick checks of a running container (used by ci.yml)
│   ├── workflows/backlog.yml        # GitHub Actions: turns backlog.json into Issues
│   ├── backlog.json                 # The app's goals, written as user stories
│   └── ISSUE_TEMPLATE/              # Forms for new user stories and bug reports
├── src/test/java/com/orthodox/orthodoxapp/
│   ├── FastingRuleTest.java         # Tests for the fasting rules
│   ├── DatabaseFixesTest.java       # An old database layout gets repaired at startup
│   ├── account/, admin/             # Tests for the profile, appearance, photos and the Members page
│   ├── notify/                      # Tests for notifications: encryption (RFC example), sending, timing, settings
│   └── auth/                        # Tests for passwords, phones, Google, staying signed in, the whole sign-in flow
└── src/main/
    ├── java/com/orthodox/orthodoxapp/
    │   ├── OrthodoxappApplication.java  # Starting point: main() boots Spring
    │   ├── DatabaseUrl.java             # Online: turns the host's DATABASE_URL into database settings
    │   ├── DatabaseFixes.java           # At startup: table repairs Hibernate can't make (old columns, new choices)
    │   ├── CalendarController.java      # Web endpoints: /api/today, /api/day/{y}/{m}/{d}
    │   ├── CalendarService.java         # Business logic: converts Orthocal data, applies rules
    │   ├── CalendarCache.java           # Keeps each day's calendar until midnight (Central)
    │   ├── OrthocalClient.java          # Calls the Orthocal API (with timeouts)
    │   ├── OrthocalDay.java             # Mirror of Orthocal's JSON (input)
    │   ├── LiturgicalDay.java           # Our clean format sent to the frontend (output)
    │   ├── FastingRule.java             # Enum: STRICT, WINE_ONLY, WINE_AND_OIL, ...
    │   ├── ReadingSelector.java         # Picks the day's readings the way the Archdiocese lists them
    │   ├── ScriptureBooks.java          # "Galatians 1.11-19" -> "St. Paul's Letter to the Galatians 1:11-19"
    │   ├── ApiExceptionHandler.java     # Turns errors into clear JSON replies
    │   ├── SpaController.java           # Sends the React app for /bible, /prayers, /profile... (bookmarks work), and /privacy
    │   ├── account/                     # Who you are: AppUser (database table), your account settings
    │   │   ├── AppUser.java, AppUserRepository.java, TextSize.java, Theme.java, AppStyle.java
    │   │   ├── AccountController.java   # /api/account/...: profile, photo, appearance, phone
    │   │   ├── AccountService.java, AccountView.java
    │   │   └── ProfilePhoto*.java       # Photo table, and checking/cropping/re-saving uploads
    │   ├── admin/                       # The Members page: Admins.java (who), MembersController.java (/api/admin/members)
    │   ├── notify/                      # Notifications: NotificationService (what and when), NotificationScheduler (every minute),
    │   │                                #   PushSender, WebPushCrypto, VapidKeys, PushSubscription, NotificationController
    │   └── auth/                        # Signing in
    │       ├── SecurityConfig.java      # Who can see what, CSRF, staying signed in, sign-out, Google
    │       ├── AuthController.java      # /api/auth/...: register, sign in
    │       ├── AuthService.java         # Password checks, lockout, one account per email, Google accounts
    │       ├── PasswordPolicy.java      # The strong-password rules
    │       ├── SessionSignIn.java       # Marks a session as signed in
    │       ├── StaySignedIn.java, ServerSecret.java  # The long-lasting signed cookie, and its secret key
    │       └── SocialClientRegistrations.java, SocialSignInHandler.java, ...
    └── resources/
        ├── application.properties       # App settings (port, database, secrets file)
        ├── application-prod.properties  # Extra settings used only online (PostgreSQL, https)
        ├── bible/                       # The Bible text (public domain); see its README.md
        └── static/                      # The BUILT React app (made by "npm run build"; not in Git)
```

Every code file has comments explaining what each part does.

---

## Getting Started (Windows + VS Code)

### 1. Install the prerequisites

- **Java 17 or newer**. Check by running `java -version` in a terminal. If you
  need it, install **Temurin** from [adoptium.net](https://adoptium.net).
- **VS Code** with these extensions:
  - *Extension Pack for Java* (Microsoft)
  - *Spring Boot Extension Pack* (VMware/Broadcom)
- **Git** (to save your progress and push to GitHub).
- **Node.js LTS** (runs the React app's tools). Check with `node --version`;
  install from [nodejs.org](https://nodejs.org) if needed.

You don't need to install Maven. VS Code's Java extension reads `pom.xml` and
downloads the libraries itself, and the **Maven Wrapper** runs Maven from a
terminal. In VS Code's terminal (**Ctrl+`**):

```powershell
.\mvnw.cmd test
```

runs every automated test (the first run downloads Maven, about 10 MB).

### 2. Open the project

1. In VS Code: **File → Open Folder…** → choose `OrthodoxChristianApp`.
2. Wait 1–2 minutes the first time. The Java extension downloads Spring Boot
   (watch the status bar at the bottom). Trust the workspace if asked.

### 3. Build the React app (once, and after changing it)

In VS Code's terminal (**Ctrl+`**):

```powershell
cd frontend
npm install
npm run build
cd ..
```

`npm install` downloads the React app's packages (only needed the first time
and when they change). `npm run build` builds the app into
`src/main/resources/static`, where the backend serves it.

### 4. Run it

Use any of these:

- Press **F5** (if asked, choose **Java**).
- Open `OrthodoxappApplication.java` and click **Run** above `main`.
- Use the **Spring Boot Dashboard** in the left sidebar and click ▶ next to `orthodoxapp`.

When the terminal shows `Started OrthodoxappApplication`, open:

| URL | What you'll see |
|---|---|
| http://localhost:8080 | The app, on the Home tab (use ‹ Prev / Next › or the date picker) |
| http://localhost:8080/?date=2026-12-25 | Home for a specific date |
| http://localhost:8080/bible/PSA/50 | The Bible tab (here at Psalm 50) |
| http://localhost:8080/prayers/morning | The Prayers tab (here at Morning Prayers) |
| http://localhost:8080/profile | The Profile tab (sign in first) |
| http://localhost:8080/profile/members | The Members page (administrators only; see below) |
| http://localhost:8080/sign-in | Sign in, or create an account |
| http://localhost:8080/privacy | The privacy policy |
| http://localhost:8080/api/today | Today's raw JSON from our backend |
| http://localhost:8080/api/day/2026/12/25 | Raw JSON for any date |
| http://localhost:8080/api/bible/JOH/3 | Raw JSON for one Bible chapter |

To stop the app, click the red ■ square in the toolbar or press **Shift+F5**.

### 5. Setting up Google sign-in and the Members page

Both are optional on your computer (online they're already set up). Keys go
in **`secrets.properties`**, which Git ignores. Copy
`secrets.example.properties` to `secrets.properties`, fill in a section, and
restart the app. The example file explains each setting.

#### Sign in with Google

This was done on 2026-10-24 for the Google Cloud project **Orthodox Daily**
(`orthodox-daily-512617`). To do it again, or for another project:

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and sign
   in with your Google account (accept Google Cloud's terms the first time).
2. Create a project: the project menu at the top → **New project** → name it
   *Orthodox Daily* → **Create**, then make sure it's selected.
3. Open **Google Auth Platform** (search for it at the top) → **Get started**:
   app name *Orthodox Daily*, your email as the support email, audience
   **External**, your email as the contact, agree to Google's User Data
   Policy → **Create**.
4. **Clients** → **Create client** → type **Web application**, name
   *Orthodox Daily web*. Under **Authorized redirect URIs** add both:
   - `http://localhost:8080/login/oauth2/code/google`
   - `https://orthodox-daily.onrender.com/login/oauth2/code/google`
5. **Create**. Copy the **Client ID** and **Client secret** it shows (Google
   shows the secret only this once; if it's lost, add a new secret to the client).
6. **Branding**: application home page `https://orthodox-daily.onrender.com`,
   privacy policy `https://orthodox-daily.onrender.com/privacy`; authorized
   domain `orthodox-daily.onrender.com` → **Save**.
7. **Audience** → **Publish app** → **Confirm**, so everyone (not only test
   users) can sign in. The app only asks for name and email and has no logo,
   so Google doesn't need to review it.
8. Put the two values in `secrets.properties` (`app.auth.google.client-id`,
   `app.auth.google.client-secret`) for your computer, and in Render →
   orthodox-daily → **Environment** as `APP_AUTH_GOOGLE_CLIENTID` and
   `APP_AUTH_GOOGLE_CLIENTSECRET` for the live app. Restart / save.

The client secret is a password for the app: never put it in a file that goes
to GitHub.

#### The Members page (administrators)

Administrators see **Members** at the top of the Profile tab: how many
accounts there are, and each one's name, email, phone number, how they sign
in and when they joined, with a button to download the list as a spreadsheet.
To be an administrator, both must be true:

1. Your email is in the `app.admin.emails` setting (`secrets.properties`), or
   online in Render's `APP_ADMIN_EMAILS` (several emails: separate them with
   commas). Online it's set to the project owner's email.
2. You've signed in with **Continue with Google** at least once, which proves
   the email is yours.

Nobody can see anyone's password, administrators included: the app only keeps
a BCrypt hash, which can check a password but can't be turned back into it.

---

## Watching the App Get Built (Live Preview)

The app is set up so you can watch changes appear while it runs:

1. **Start the backend** (F5) and leave it running.
2. **Start the React dev server** in a second terminal:
   ```powershell
   cd frontend
   npm run dev
   ```
3. Open **http://localhost:5173** (not 8080) and keep the tab open.
4. When a file changes (whether you edit it or Claude does):
   - **React files (`frontend/src/...`):** the page updates by itself, instantly.
   - **Java files:** VS Code recompiles on save, and **Spring Boot DevTools**
     restarts the backend in about a second.
5. Use **VS Code's Source Control panel** (Ctrl+Shift+G) to see exactly what
   changed in each file. Green lines are added; red lines are removed.

### On your iPhone (or any phone)

Your phone must be on the **same Wi-Fi** as your computer.

1. Find your computer's address: run `ipconfig` and look for **IPv4 Address**
   under "Wireless LAN adapter Wi-Fi" (for example `192.168.4.49`).
2. In Safari on the phone, open one of:
   - `http://192.168.4.49:8080`: the built app (after `npm run build`), or
   - `http://192.168.4.49:5173`: the live dev server (updates as code changes).
     The first time, Windows asks whether Node.js may use the network: choose
     **Allow**.
3. To use it like an app: tap **Share** → **Add to Home Screen**. It gets the
   gold cross icon and opens full screen.

These addresses use `http`, not `https`. That's fine for trying things out;
features that need `https` (location for "parishes near me", Google sign-in on
the phone, working offline) work on the online address instead (see
[Putting the App Online](#putting-the-app-online)).

**Tip:** Commit after each working step (Source Control → type a message →
✓ Commit → Sync). Your GitHub history then becomes a timeline of the build.

---

## Putting the App Online

The app runs online at **https://orthodox-daily.onrender.com**, on
[Render](https://render.com), which builds it straight from this GitHub
repository. Three files make that work:

| File | What it does |
|---|---|
| `Dockerfile` | Packs the React app, the backend and Java into one container image |
| `render.yaml` | Tells Render what to create: the web app (from the `Dockerfile`) and a PostgreSQL database |
| `src/main/resources/application-prod.properties` | Settings used only online: PostgreSQL instead of H2, https, smaller memory limits |

Online, accounts live in **PostgreSQL** instead of H2, because files inside
the app's container are wiped on every restart and update. Render gives the
app the database's address as `DATABASE_URL`, and `DatabaseUrl.java` turns it
into the settings Spring Boot needs.

### The first time (about 15 minutes)

1. Go to [render.com](https://render.com) and **sign up with GitHub** (the
   `kenneth474` account). When GitHub asks which repositories Render may see,
   choose **OrthodoxChristianApp**.
2. In Render's dashboard: **New** → **Blueprint**, then pick
   **OrthodoxChristianApp**.
3. Render reads `render.yaml` and shows what it will create:
   **orthodox-daily** (web service, Free) and **orthodox-daily-db**
   (PostgreSQL, Free). Name the Blueprint (e.g. *Orthodox Daily*) and click
   **Deploy Blueprint**.
4. Wait for the first build, about 5 to 10 minutes. You can watch it under
   orthodox-daily → **Logs**.
5. When it says **Live**, open the address at the top of the page, for
   example `https://orthodox-daily.onrender.com` (Render adds a few letters if
   that name is taken).
6. On your iPhone: open that address in Safari → **Share** → **Add to Home
   Screen**.

Never put card details, passwords or keys in a file in this project. Anything
like that is typed only into Render's own pages.

### After that, updates are automatic

Every push to `main` runs the **Build and test** checks on GitHub. When they
pass, Render builds and starts the new version by itself
(`autoDeployTrigger: checksPass` in `render.yaml`). If a check fails, the
working version stays online.

The second check, **Container (as it runs online)**, builds the container the
same way Render does, starts it with a real PostgreSQL database on a server
as small as Render's free one, and tries it out
(`.github/scripts/smoke-test.sh`). Its log also says how long the app took to
start.

### What the free plan means

| Limit | What happens | When it matters |
|---|---|---|
| [Sleeps after 15 minutes](https://render.com/docs/free) with no visitors | A sleeping app can't send notifications, so `keep-awake.yml` visits it every 10 minutes. That uses about 744 of the 750 free hours a month, so it only works for one free service | Upgrade the web service to **Starter** (about $7/month) for always-on, then delete `keep-awake.yml` |
| [The free database expires after 30 days](https://render.com/docs/free) | Render emails a warning; after a 14-day grace period the database, with every account in it, is deleted | Upgrade the database (about $6/month) **before** people rely on their accounts |
| [Email can't be sent](https://render.com/changelog/free-web-services-will-no-longer-allow-outbound-traffic-to-smtp-ports) | Free services can't reach email servers (SMTP). The app sends no email today; it will matter for password resets | Upgrade to Starter, or send email through a service's web API (Issue #32) |

Prices change; check [render.com/pricing](https://render.com/pricing). To
upgrade: the service or database in Render → **Settings** → **Instance Type**.

### Secret settings online

`secrets.properties` stays on your computer: it never goes to GitHub or
Render. Online, the same settings are **environment variables**: in Render,
open **orthodox-daily** → **Environment** → **Add Environment Variable**, then
save (the app restarts with them). The name is the setting in capitals, with
`_` instead of `.` and no `-`:

| In `secrets.properties` | Render environment variable | Set online? |
|---|---|---|
| `app.auth.google.client-id` | `APP_AUTH_GOOGLE_CLIENTID` | ✅ |
| `app.auth.google.client-secret` | `APP_AUTH_GOOGLE_CLIENTSECRET` | ✅ |
| `app.admin.emails` | `APP_ADMIN_EMAILS` | ✅ |

For Google sign-in online, also add
`https://orthodox-daily.onrender.com/login/oauth2/code/google` (your real
address) as an **Authorized redirect URI** in Google Cloud Console, next to the
`localhost` one.

### Seeing who has an account

Open the app, sign in with **Continue with Google** (with the email in
`APP_ADMIN_EMAILS`), and go to **Profile** → **Members**. It shows the number
of accounts and, for each member, their name, email, phone number, whether
they sign in with a password or Google, and when they joined. **Download as a
spreadsheet (CSV)** saves the list for Excel, Numbers or Google Sheets.

Passwords can't be shown, to you or anyone: only a one-way BCrypt hash of
each is stored. If a member forgets theirs and their email is a Gmail address,
they can use **Continue with Google** (resetting a password by email is on the
roadmap).

The database itself is locked so that only Render's own services can reach it
(`ipAllowList: []` in `render.yaml`), so the Members page is the way in. To
open it with a database tool one day, you'd add your own internet address to
that `ipAllowList` and connect with the database's **External Database URL**
from Render (it contains the database password, so keep it private).

### Moving to another host later

Nothing in the app is tied to Render except `render.yaml`. Any host that runs
containers (Railway, Fly.io, Google Cloud Run, your own server) can build the
same `Dockerfile`. It needs:

- a PostgreSQL database, with its address in a `DATABASE_URL` environment
  variable (`postgresql://USER:PASSWORD@HOST:5432/NAME`);
- the same secret environment variables as above.

To bring the accounts along, copy the database across with PostgreSQL's
`pg_dump` and `pg_restore` tools.

---

## Roadmap

### How we track work

- **User stories:** every goal is a GitHub [Issue](https://github.com/kenneth474/OrthodoxChristianApp/issues),
  written as *"As a …, I want …, so that …"* with a checklist of acceptance criteria.
  Each one has a phase [milestone](https://github.com/kenneth474/OrthodoxChristianApp/milestones)
  and labels such as `parishes` or `accounts`.
- **Board:** the [Projects](https://github.com/kenneth474/OrthodoxChristianApp/projects) tab shows
  every story in **Todo → In Progress → Done** columns.
- **Done means done:** put `Closes #12` (the Issue's number) in a commit message.
  When it's pushed, GitHub closes Issue #12 and the board moves it to **Done**.
- **Actions:** every push runs the build and tests ([ci.yml](.github/workflows/ci.yml)).
  New stories added to [backlog.json](.github/backlog.json) become Issues automatically
  ([backlog.yml](.github/workflows/backlog.yml)), or you can use **New issue → User story**.

**Phase 1: Liturgics** *(antiochian.org: Liturgics, Spiritual Readings)*
- [x] Spring Boot project and `/api/today` endpoint (Greek/Antiochian, New calendar)
- [x] Preview page showing fast, readings and saints
- [x] `FastingRule` enum and a controller → service → client layer structure
- [x] Any-date lookups (`/api/day/2026/12/25`), full scripture text, saints' lives
- [x] Error handling (Orthocal offline, slow network, impossible dates)
- [x] Automated tests and GitHub Actions build on every push

**Phase 2: Find a Parish** *(antiochian.org: Parishes)*
- [ ] Database with Spring Data JPA (H2 to start, PostgreSQL later)
- [ ] `Parish` entity: name, address, phone, website, diocese, latitude/longitude
- [ ] "Near me" search using the browser's location and the Haversine distance formula
- [ ] Search by city, state or ZIP; filter by diocese; map and directions links

**Phase 3: Accounts**
- [x] Spring Security: sign up and sign in with sessions, BCrypt password hashes, CSRF protection
- [x] Strong password rules with a live checklist; lockout after 5 wrong tries
- [x] An email for every account (one account per email); an optional phone number (not checked with a code)
- [x] Sign in with Google: creates your account, or opens the one with your email. (Sign in with Apple was dropped: it needs a $99/year Apple Developer account)
- [x] Stay signed in until you sign out (a signed cookie that outlasts server restarts)
- [x] Members page for administrators (count, names, emails, phones; never passwords)
- [ ] Two-step verification and checking emails and phones with codes: built in October 2026, then taken out until after launch (it's in the Git history)
- [ ] Reset a forgotten password (needs email, which Render's free plan blocks: Issue #32)
- [x] Profile: photo, patron saint with their feast day, name day greeting, home parish (free text for now)
- [x] Text size (small, normal, large) and dark mode
- [ ] Roles: member, parish admin (site administrators: done, by email)
- [ ] Home parish linked to the parish finder; bookmarked saints and feasts

**Phase 4: Content** *(antiochian.org: Church News, Orthodox Prayers, Dioceses, Ministries, About)*
- [ ] News articles and parish events with an admin editor (CRUD)
- [x] Bottom tab bar: Home, Bible, Prayers, Profile
- [x] The Orthodox Bible (Septuagint + King James New Testament)
- [x] Prayer book (traditional prayers; the Archdiocese's own translations with its permission)
- [x] Church tab: hierarchs, diocese and ministry pages, and a daily prayer intention (parish lists come with the parish finder)
- [x] Link each day to the Archdiocese's Online Liturgical Guide (service texts)
- [ ] Read and download the day's service texts in the app (waiting on permission)

**Phase 5: Real app and every platform**
- [x] React frontend (Vite + TypeScript) replacing the preview pages; adds to a phone's home screen
- [x] Calendar caching (in memory, until midnight Central; an older copy if Orthocal is down)
- [x] Five Orthodox styles, each light and dark; tabs that remember where you were; pull to refresh
- [x] Notifications (Web Push): the day's feast, fast and readings at a time each person chooses, and a name-day greeting
- [ ] Parish events in the daily notification (once parish events exist, Issue #18)
- [ ] PWA install support, then Android/iOS apps with Capacitor
- [x] Put the app online: [orthodox-daily.onrender.com](https://orthodox-daily.onrender.com) on Render (`Dockerfile`, `render.yaml`, PostgreSQL), tested in GitHub Actions before every deploy
- [ ] Before people rely on it: upgrade Render's free database (deleted after 30 days)

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Port 8080 was already in use` | Another copy is still running. Stop it (red ■), or change `server.port` in `application.properties`. |
| "Continue with Google" is greyed out | Fill in both Google settings (`secrets.properties`, or Render's Environment online), then restart the app. |
| Google says "redirect_uri_mismatch" | The address in the error must be listed exactly under **Authorized redirect URIs** in Google Cloud Console (see [Sign in with Google](#sign-in-with-google)). |
| After "Continue with Google" my password no longer works | That's on purpose: the first Google sign-in proves the email is yours, and removes a password the app couldn't check was yours. Keep using Google. |
| I'm still being signed out | Signing out yourself, private browsing, or clearing the browser's cookies all sign you out. Otherwise you stay signed in for as long as you open the app at least once every 400 days. |
| There's no **Members** in my Profile | Your email must be in `APP_ADMIN_EMAILS` (or `app.admin.emails` on your computer), and you must have signed in with Google at least once. |
| No notifications on my iPhone | They work only in the app added to the Home Screen (iOS 16.4 or later), opened from there. Then Profile → Notifications → **Turn on notifications** → **Allow**, and try **Send a test notification**. If you tapped Don't Allow: iPhone Settings → Notifications → Orthodox Daily. |
| Notifications come late | On the free plan the app may have been asleep; GitHub's keep-awake visits can run a few minutes late. Anything up to 3 hours late is still sent. |
| No voice menu in the reading player | It appears once the device lists at least two American English voices. iPhones list theirs a moment after the app opens; start reading once, and the menu appears. |
| I want an empty database | Stop the app and delete the `data/` folder. It's recreated on the next start. |
| Red squiggles everywhere after opening | The Java extension is still downloading libraries. Wait, or run **Ctrl+Shift+P → Java: Clean Java Language Server Workspace**. |
| Page says *"Could not load this day"* | Make sure the app is running and you're online. Check the VS Code terminal for an error. |
| http://localhost:8080 shows a blank page or "Not Found" | Build the React app: `cd frontend`, `npm install`, `npm run build`, then restart the backend. |
| Phone can't open the app | Same Wi-Fi as the computer? Right address (`ipconfig`)? For port 5173, allow Node.js through Windows Firewall when asked. |
| Startup error about `/api/today` being mapped twice | An old copy of `TodayController.java` still has code in it. It should contain only comments; you can delete it. |
| `java -version` shows 11 or older | Install Java 17+ from adoptium.net, then restart VS Code. |
| The online app takes a minute to open | On Render's free plan it sleeps after 15 minutes without visitors and wakes on the next visit. Starter plan = always on. |
| A Blueprint sync failed with "name already in use" | Click **Manual sync** on the Blueprint once more. (The very first sync did this; the second one created the web service.) |
| Render shows "Deploy failed" | Open the deploy's log in Render. If the app started but stopped with `'url' must start with "jdbc"`, the `DATABASE_URL` environment variable is missing. |
| My push didn't update the online app | Render waits for the GitHub checks. A red ✗ in the **Actions** tab means a check failed, so the old version stays online. |

---

## Credits

- Liturgical calendar data: [orthocal.info](https://orthocal.info), by Brian Glass
  ([source code](https://github.com/brianglass/orthocal-python)).
- Bible: Sir Lancelot Brenton's English Septuagint (1851; updated spelling by
  Adam Boyd, CC0) and the King James Version New Testament, both public domain,
  from [eBible.org](https://ebible.org). See `src/main/resources/bible/README.md`.
- Prayers: traditional Orthodox prayers in their common English wording. The
  Archdiocese's own prayer book translations can be added with its permission.
- Fasting guidance shown in the app follows strict typikon practice. Always
  follow the direction of your parish priest.
- Feature ideas follow [antiochian.org](https://www.antiochian.org). Content
  and data from the Archdiocese are used only with its permission.
