/*
  listen.ts: reads text aloud with the device's own reading voice.

  It uses the browser's built-in Web Speech API (speechSynthesis), which
  speaks with the voices installed on the phone or computer, the same ones
  screen readers use. Only voices stored ON the device are used
  (voice.localService): some browsers also offer online "natural" voices that
  send the text to a cloud AI service, and those are skipped. And only three
  voices are offered (see offeredVoices below), not the dozens some devices have.

  HOW REACT SEES IT: this file keeps its own little "store": the current
  state (playing? paused? which title?) plus a list of listeners. React
  components read it with the useListen() hook, and re-draw whenever it
  changes, through React's useSyncExternalStore.
*/
import { useSyncExternalStore } from 'react'

export interface ListenState {
  playing: boolean
  paused: boolean
  title: string
  voices: SpeechSynthesisVoice[] // the (up to) three voices offered on this device
  voiceName: string
  rate: string // "0.8" .. "1.15"
}

const VOICE_KEY = 'orthodoxDaily.voice'
const RATE_KEY = 'orthodoxDaily.rate'

export const listenSupported = typeof window !== 'undefined' && 'speechSynthesis' in window

function load(key: string, fallback: string): string {
  try {
    return localStorage.getItem(key) ?? fallback
  } catch {
    return fallback
  }
}
function save(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // storage switched off: the setting just won't be remembered
  }
}

// ----- The store -----

let state: ListenState = {
  playing: false,
  paused: false,
  title: '',
  voices: [],
  voiceName: load(VOICE_KEY, ''),
  rate: load(RATE_KEY, '1'),
}
const listeners = new Set<() => void>()

// Every change makes a NEW state object (never edits the old one), which is
// how React notices that something changed.
function setState(changes: Partial<ListenState>) {
  state = { ...state, ...changes }
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** The hook components use: const listen = useListen() */
export function useListen(): ListenState {
  return useSyncExternalStore(subscribe, () => state)
}

// ----- Voices -----

// Windows' three American voices, offered in this order.
const WINDOWS_VOICES = ['David', 'Mark', 'Zira']

// American voices on iPhones, iPads and Macs, best first. A new iPhone comes
// with Samantha plus the synthetic "Eloquence" voices (Reed, Shelley,
// Sandy...); better ones ("Premium", "Enhanced") appear once downloaded in
// Settings > Accessibility > Spoken Content > Voices.
const APPLE_VOICES = ['Samantha', 'Ava', 'Evan', 'Zoe', 'Allison', 'Tom', 'Susan', 'Nathan', 'Joelle', 'Noelle',
  'Alex', 'Aaron', 'Nicky', 'Reed', 'Shelley', 'Sandy', 'Eddy', 'Flo', 'Rocko']

// Joke and sound-effect voices that iPhones and Macs include: never offered.
const NOVELTY_VOICES =
  /\b(Albert|Bad News|Bahh|Bells|Boing|Bubbles|Cellos|Good News|Jester|Organ|Superstar|Trinoids|Whisper|Wobble|Zarvox)\b/

/** A voice's short name: "Microsoft David - English (United States)" -> "David", "Reed (English (US))" -> "Reed". */
export function voiceLabel(voice: SpeechSynthesisVoice): string {
  return voice.name.replace(/^(Microsoft|Google|Apple)\s+/, '').split(/[\s(]/)[0] || voice.name
}

/**
 * The voices offered: three American English voices (fewer only if the
 * device has fewer).
 *  - On Windows: David, Mark and Zira.
 *  - Elsewhere (iPhone, Mac, Android), where the voices have other names:
 *    "Premium" and "Enhanced" voices first, then the list above, each name
 *    once (so not "Samantha" and "Samantha (Enhanced)").
 * Only voices stored on the device count: online ones send the text away.
 * Exported for the tests in lib.test.ts.
 */
export function offeredVoices(all: SpeechSynthesisVoice[]): SpeechSynthesisVoice[] {
  const american = all.filter(
    (v) => v.localService && v.lang.replace('_', '-').toLowerCase() === 'en-us' && !NOVELTY_VOICES.test(v.name),
  )

  const windows = WINDOWS_VOICES.map((name) => american.find((v) => v.name.startsWith(`Microsoft ${name}`)))
  const found = windows.filter((v): v is SpeechSynthesisVoice => v !== undefined)
  if (found.length > 0) return found

  const rank = (v: SpeechSynthesisVoice) => {
    const quality = /Premium/.test(v.name) ? 0 : /Enhanced/.test(v.name) ? 1 : 2
    const known = APPLE_VOICES.indexOf(voiceLabel(v))
    return quality * 100 + (known === -1 ? 50 : known)
  }
  const offered: SpeechSynthesisVoice[] = []
  for (const voice of [...american].sort((a, b) => rank(a) - rank(b))) {
    if (!offered.some((v) => voiceLabel(v) === voiceLabel(voice))) offered.push(voice)
  }
  return offered.slice(0, 3)
}

function localVoices(): SpeechSynthesisVoice[] {
  if (!listenSupported) return []
  return offeredVoices(speechSynthesis.getVoices())
}

// The voice picked in the player, or else the first one offered.
function chosenVoice(): SpeechSynthesisVoice | undefined {
  const voices = state.voices.length ? state.voices : localVoices()
  return voices.find((v) => v.name === state.voiceName) ?? voices[0]
}

// Devices can list their voices a moment after the page loads. Most say so
// ("voiceschanged"), but iPhones don't always, so look again a few times.
function refreshVoices() {
  const voices = localVoices()
  if (voices.map((v) => v.name).join() !== state.voices.map((v) => v.name).join()) setState({ voices })
}

if (listenSupported) {
  refreshVoices()
  speechSynthesis.addEventListener?.('voiceschanged', refreshVoices)
  for (const wait of [300, 1000, 3000]) window.setTimeout(refreshVoices, wait)
  window.addEventListener('pagehide', () => stop()) // stop talking when leaving the page
}

// ----- Preparing the text -----

/** Voices may read "St." as "Street", so spell out Saint/Saints; tidy spaces. */
export function forSpeaking(text: string): string {
  return text
    .replace(/\bSts\.?\s+(?=[A-Z])/g, 'Saints ')
    .replace(/\bSt\.?\s+(?=[A-Z])/g, 'Saint ')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * Splits text into sentences, then joins short ones, so each piece is up to
 * about 200 characters. Browsers stop very long utterances part-way, and
 * pieces let Pause/Resume pick up close to where they left off.
 */
export function toChunks(text: string): string[] {
  const sentences = forSpeaking(text).match(/[^.!?;:]+[.!?;:]*["”’)]*\s*/g) ?? []
  const pieces: string[] = []
  let current = ''
  for (const sentence of sentences) {
    if ((current + sentence).length > 200 && current) {
      pieces.push(current.trim())
      current = ''
    }
    current += sentence
  }
  if (current.trim()) pieces.push(current.trim())
  return pieces
}

// ----- Speaking -----

let chunks: string[] = []
let index = 0
let turn = 0 // counts spoken pieces, so events from a cancelled piece are ignored

function speakNext() {
  if (!state.playing || state.paused) return
  if (index >= chunks.length) {
    stop()
    return
  }
  const utterance = new SpeechSynthesisUtterance(chunks[index])
  const voice = chosenVoice()
  // With no voices listed yet (iPhones, sometimes), asking for American
  // English still gets the device's American voice.
  utterance.lang = 'en-US'
  if (voice) utterance.voice = voice
  utterance.rate = Number(state.rate)
  // Once speech starts, an iPhone has surely listed its voices: show them.
  utterance.onstart = refreshVoices

  // When this piece finishes, move on, unless it was cancelled (Pause, Stop,
  // a new voice), which some browsers also report as "finished".
  const myTurn = ++turn
  const next = () => {
    if (myTurn === turn && state.playing && !state.paused) {
      index++
      speakNext()
    }
  }
  utterance.onend = next
  utterance.onerror = next
  speechSynthesis.speak(utterance)
}

// Cancels whatever is being said, so its events are ignored. (Only when
// something is being said: an iPhone can drop a new sentence that comes
// straight after a cancel.)
function silence() {
  turn++
  if (listenSupported && (speechSynthesis.speaking || speechSynthesis.pending)) speechSynthesis.cancel()
}

/** Start reading a text aloud; the player bar shows the title. */
export function play(title: string, text: string) {
  if (!listenSupported) return
  silence()
  chunks = toChunks(text)
  index = 0
  setState({ playing: true, paused: false, title })
  refreshVoices()
  speakNext()
}

/** Pausing cancels the current sentence; resuming starts it again. */
export function togglePause() {
  setState({ paused: !state.paused })
  silence()
  if (!state.paused) speakNext()
}

export function stop() {
  silence()
  if (state.playing) setState({ playing: false, paused: false })
}

export function setRate(rate: string) {
  save(RATE_KEY, rate)
  setState({ rate }) // used from the next sentence
}

export function setVoice(name: string) {
  save(VOICE_KEY, name)
  setState({ voiceName: name })
  if (state.playing && !state.paused) {
    silence() // switch voice from the current sentence
    speakNext()
  }
}
