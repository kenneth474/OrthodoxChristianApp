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

// Joke and sound-effect voices that iPhones and Macs include ("Bubbles",
// "Zarvox"...), and old robotic ones: never offered.
const NOVELTY_VOICES =
  /\b(Albert|Bad News|Bahh|Bells|Boing|Bubbles|Cellos|Eddy|Flo|Fred|Good News|Grandma|Grandpa|Jester|Junior|Kathy|Organ|Ralph|Reed|Rocko|Sandy|Shelley|Superstar|Trinoids|Whisper|Wobble|Zarvox)\b/

/**
 * The voices offered, at most three:
 *  - on Windows: David, Mark and Zira
 *  - elsewhere (iPhone, Mac, Android), where voices have other names: up to
 *    three American English voices, the better-sounding "Premium" and
 *    "Enhanced" ones and the device's own choice first; or other English
 *    voices if there are no American ones.
 * Exported for the tests in lib.test.ts.
 */
export function offeredVoices(all: SpeechSynthesisVoice[]): SpeechSynthesisVoice[] {
  const english = all.filter((v) => v.localService && v.lang.toLowerCase().startsWith('en'))

  const windows = WINDOWS_VOICES.map((name) => english.find((v) => v.name.startsWith(`Microsoft ${name}`)))
  const found = windows.filter((v): v is SpeechSynthesisVoice => v !== undefined)
  if (found.length > 0) return found

  const usable = english.filter((v) => !NOVELTY_VOICES.test(v.name))
  const american = usable.filter((v) => v.lang.replace('_', '-').toLowerCase() === 'en-us')
  const rank = (v: SpeechSynthesisVoice) =>
    /Premium/.test(v.name) ? 0 : /Enhanced/.test(v.name) ? 1 : v.default ? 2 : 3
  return [...(american.length > 0 ? american : usable)].sort((a, b) => rank(a) - rank(b)).slice(0, 3)
}

function localVoices(): SpeechSynthesisVoice[] {
  if (!listenSupported) return []
  return offeredVoices(speechSynthesis.getVoices())
}

function chosenVoice(): SpeechSynthesisVoice | undefined {
  const voices = state.voices.length ? state.voices : localVoices()
  return (
    voices.find((v) => v.name === state.voiceName) ??
    voices.find((v) => v.default) ??
    voices.find((v) => v.lang === 'en-US') ??
    voices[0]
  )
}

// Some browsers load their list of voices a moment after the page.
if (listenSupported) {
  setState({ voices: localVoices() })
  speechSynthesis.addEventListener('voiceschanged', () => setState({ voices: localVoices() }))
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
  if (voice) {
    utterance.voice = voice
    utterance.lang = voice.lang
  }
  utterance.rate = Number(state.rate)

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

// Cancels whatever is being said, so its events are ignored.
function silence() {
  turn++
  if (listenSupported) speechSynthesis.cancel()
}

/** Start reading a text aloud; the player bar shows the title. */
export function play(title: string, text: string) {
  if (!listenSupported) return
  silence()
  chunks = toChunks(text)
  index = 0
  setState({ playing: true, paused: false, title, voices: localVoices() })
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
