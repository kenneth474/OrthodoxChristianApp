# Orthodox Daily: React frontend

The app's screens, written in **React** with **TypeScript** and built with
**Vite**. The Spring Boot backend (the rest of this repository) provides the
data through its `/api/...` endpoints.

## Commands (run them in this `frontend` folder)

| Command | What it does |
|---|---|
| `npm install` | Downloads the packages listed in `package.json` (once, and after they change) |
| `npm run dev` | Runs the app at http://localhost:5173 and reloads it whenever you save a file. Needs the backend running too (F5 in VS Code). Also reachable from your phone on the same Wi-Fi. |
| `npm run build` | Checks the types, then builds the finished app into `../src/main/resources/static`, where Spring Boot serves it at http://localhost:8080 |
| `npm test` | Runs the tests (`*.test.ts`) |
| `npm run lint` | Checks the code for common mistakes |

## Where things are

```
frontend/
├── index.html              The one HTML page (home-screen icon, style set before drawing)
├── vite.config.ts          Dev server, proxy to the backend, build output folder, tests
├── public/                 Copied as-is: icons, manifest.webmanifest, data/prayers.json, data/church.json,
│                           and privacy.html (the privacy policy, a plain page outside the app)
└── src/
    ├── main.tsx            Where React starts (and the fonts are loaded)
    ├── App.tsx             The addresses, and the shared layout
    ├── api.ts              Talking to the backend (JSON, CSRF token, errors)
    ├── types.ts            The shapes of the backend's data (mirrors the Java records)
    ├── account-context.ts  useAccount(): who's signed in, from any screen
    ├── AccountProvider.tsx Loads the signed-in account once for the whole app
    ├── tabs-context.ts     useTabs(): which tab is showing, where each tab was left
    ├── components/         TabsHost (keeps every tab alive), TabBar, PullToRefresh,
    │                       ornaments (cross, feast signs, page header), StylePicker, Player
    ├── pages/              One file per screen: Home, Bible, Prayers, Church, Profile, Sign-in,
    │                       Members (for administrators)
    ├── lib/                Plain helpers: data loading (data.ts), styles, Church seasons and
    │                       Pascha, the daily intention, tabs, dates, phone numbers, reading
    │                       aloud (and which three voices to offer), prayers
    └── styles/             theme.css (the five styles, light and dark) and app.css (the screens)
```

Every file starts with a comment explaining what it's for.
