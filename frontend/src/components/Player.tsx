/*
  Player: the reading-aloud bar that appears above the tab bar while
  something is being read (see lib/listen.ts). Pause/Resume, Stop, speed
  and voice.

  While it's showing, the page gets extra room at the bottom (the
  --player-height variable, used in app.css), so the bar never hides the end
  of the page.
*/
import { useLayoutEffect, useRef } from 'react'
import { setRate, setVoice, stop, togglePause, useListen, voiceLabel } from '../lib/listen'

export function Player() {
  const listen = useListen()
  const bar = useRef<HTMLDivElement>(null)

  // Measure the bar (it's taller when its buttons wrap onto two lines on a
  // phone) and give the page that much room. A ResizeObserver says whenever
  // an element's size changes.
  useLayoutEffect(() => {
    const element = bar.current
    if (!element) return
    const root = document.documentElement
    const measure = () => root.style.setProperty('--player-height', `${element.offsetHeight + 12}px`)
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => {
      observer.disconnect()
      root.style.removeProperty('--player-height')
    }
  }, [listen.playing])

  if (!listen.playing) return null // nothing being read: draw nothing

  const chosen = listen.voices.find((v) => v.name === listen.voiceName)?.name ?? listen.voices[0]?.name ?? ''

  return (
    <div className="player" role="region" aria-label="Reading aloud" ref={bar}>
      <div className="player-title">{listen.title}</div>
      <div className="player-controls">
        <button type="button" onClick={togglePause}>
          {listen.paused ? 'Resume' : 'Pause'}
        </button>
        <button type="button" onClick={stop}>
          Stop
        </button>
        <label>
          Speed
          <select value={listen.rate} onChange={(e) => setRate(e.target.value)}>
            <option value="0.8">Slower</option>
            <option value="0.9">Slow</option>
            <option value="1">Normal</option>
            <option value="1.15">Fast</option>
          </select>
        </label>
        {listen.voices.length > 1 && (
          <label>
            Voice
            <select value={chosen} onChange={(e) => setVoice(e.target.value)}>
              {listen.voices.map((v) => (
                <option key={v.name} value={v.name}>
                  {voiceLabel(v)}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
    </div>
  )
}
