/*
  Player: the reading-aloud bar that appears above the tab bar while
  something is being read (see lib/listen.ts). Pause/Resume, Stop, speed
  and voice.
*/
import { setRate, setVoice, stop, togglePause, useListen } from '../lib/listen'

export function Player() {
  const listen = useListen()
  if (!listen.playing) return null // nothing being read: draw nothing

  const chosen = listen.voices.find((v) => v.name === listen.voiceName)?.name ?? listen.voices[0]?.name ?? ''

  return (
    <div className="player" role="region" aria-label="Reading aloud">
      <div className="player-title">{listen.title}</div>
      <div className="player-controls">
        <button type="button" onClick={togglePause}>
          {listen.paused ? 'Resume' : 'Pause'}
        </button>
        <button type="button" onClick={stop}>
          Stop
        </button>
        <label>
          Speed{' '}
          <select value={listen.rate} onChange={(e) => setRate(e.target.value)}>
            <option value="0.8">Slower</option>
            <option value="0.9">Slow</option>
            <option value="1">Normal</option>
            <option value="1.15">Fast</option>
          </select>
        </label>
        {listen.voices.length > 1 && (
          <label>
            Voice{' '}
            <select value={chosen} onChange={(e) => setVoice(e.target.value)}>
              {listen.voices.map((v) => (
                <option key={v.name} value={v.name}>
                  {v.name.replace(/^(Microsoft|Google|Apple)\s+/, '')}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      {listen.voices.length === 0 && (
        <div className="player-note">
          This device doesn't have an English reading voice installed. On iPhone: Settings › Accessibility › Spoken
          Content › Voices. On Windows: Settings › Time &amp; language › Speech › Add voices.
        </div>
      )}
    </div>
  )
}
