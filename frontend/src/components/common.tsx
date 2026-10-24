/*
  common.tsx: small pieces used on several screens.
*/
import { Fragment } from 'react'
import { listenSupported, play } from '../lib/listen'
import type { Account } from '../types'

/** A "Listen" button that reads the given text aloud with the device's voice. */
export function ListenButton({ title, text }: { title: string; text: () => string }) {
  if (!listenSupported) return null
  // "text" is a function so the (possibly long) text is only built when clicked.
  return (
    <button type="button" className="listen" onClick={() => play(title, text())}>
      Listen
    </button>
  )
}

/** Your photo, or your first initial in a circle if you haven't added one. */
export function Avatar({ account, className = 'avatar' }: { account: Account; className?: string }) {
  if (account.photoVersion) {
    return <img className={className} src={`/api/account/photo?v=${account.photoVersion}`} alt="Your profile photo" />
  }
  return (
    <span className={className} aria-hidden="true">
      {account.displayName.charAt(0).toUpperCase()}
    </span>
  )
}

export interface MessageData {
  kind: 'error' | 'info'
  text: string
  problems?: string[] // e.g. password rules that weren't met
}

/** A red (error) or green (info) message box. */
export function Message({ message }: { message: MessageData | null }) {
  if (!message) return null
  return (
    <div className={`message ${message.kind}`} role={message.kind === 'error' ? 'alert' : 'status'}>
      {message.text}
      {message.problems && message.problems.length > 0 && (
        <ul>
          {message.problems.map((problem) => (
            <li key={problem}>{problem}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * Shows scripture text with the translators' added words in italics. Those
 * words are in [square brackets] in the text; split() on a pattern with a
 * (group) keeps the bracketed parts at the odd positions of the list.
 *
 * dropCap: draw the first letter large, in the style's accent color, the
 * way service books and Gospel books begin a reading.
 */
export function ScriptureText({ text, dropCap = false }: { text: string; dropCap?: boolean }) {
  const parts = text.split(/\[([^\]]+)\]/)
  return (
    <>
      {parts.map((part, i) => {
        if (i % 2 === 1) return <i key={i}>{part}</i>
        if (dropCap && i === 0 && part.length > 1) {
          return (
            <Fragment key={i}>
              <span className="dropcap">{part.charAt(0)}</span>
              {part.slice(1)}
            </Fragment>
          )
        }
        return <Fragment key={i}>{part}</Fragment>
      })}
    </>
  )
}

/** The scripture verses of a reading or chapter, in paragraphs, the first one with a drop capital. */
export function Verses({ paragraphs }: { paragraphs: { number: string | number; text: string }[][] }) {
  return (
    <>
      {paragraphs.map((paragraph, p) => (
        <p key={`${p}-${paragraph[0].number}`}>
          {paragraph.map((verse, v) => {
            const first = p === 0 && v === 0
            return (
              <span key={`${v}-${verse.number}`}>
                {/* The opening verse gets the drop capital instead of its number. */}
                {!first && <sup>{verse.number}</sup>}
                <ScriptureText text={verse.text} dropCap={first} />{' '}
              </span>
            )
          })}
        </p>
      ))}
    </>
  )
}
