/*
  Tests for the helpers in src/lib. Run them with:  npm test

  Vitest works like JUnit on the Java side: describe() groups tests, it()
  is one test, and expect(actual).toBe(expected) is like assertEquals.
  The tests run in "jsdom", a pretend browser (see vite.config.ts), so the
  HTML sanitizer can use DOMParser just as it does in a real browser.
*/
import { describe, expect, it } from 'vitest'
import type { PrayerBook } from '../types'
import { addDays, isIsoDate } from './dates'
import { forSpeaking, offeredVoices, toChunks, voiceLabel } from './listen'
import { phoneText } from './phone'
import { expandItems, spokenLine, timesLabel } from './prayerBook'
import { htmlToSpeech, sanitizeHtml } from './sanitize'
import { plainScripture, toParagraphs } from './scripture'
import { allIntentions, intentionFor, type ChurchData } from './church'
import { daysFromPascha, liturgicalSeason, orthodoxPascha } from './seasons'
import { tabFor } from './tabs'
import church from '../../public/data/church.json'

describe('dates', () => {
  it('moves across the ends of months and years', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDays('2027-01-01', -1)).toBe('2026-12-31')
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29') // leap year
  })

  it('recognises YYYY-MM-DD dates only', () => {
    expect(isIsoDate('2026-12-25')).toBe(true)
    expect(isIsoDate('12/25/2026')).toBe(false)
    expect(isIsoDate(null)).toBe(false)
  })
})

describe('sanitizeHtml (protection against XSS)', () => {
  it('keeps simple formatting', () => {
    expect(sanitizeHtml('<p>Holy <em>Apostle</em> James</p>')).toBe('<p>Holy <em>Apostle</em> James</p>')
  })

  it('removes scripts and every attribute', () => {
    const dirty = '<p onclick="steal()">Hi<script>steal()</script><img src=x onerror="steal()"></p>'
    expect(sanitizeHtml(dirty)).toBe('<p>Hi</p>')
  })

  it('keeps the text of unknown tags', () => {
    expect(sanitizeHtml('<p><a href="https://example.com">a link</a></p>')).toBe('<p>a link</p>')
  })

  it('turns paragraphs into sentences for reading aloud', () => {
    expect(htmlToSpeech('<p>First</p><p>Second</p>')).toBe('First. Second')
  })
})

describe('reading aloud', () => {
  it('says "Saint" instead of "St."', () => {
    expect(forSpeaking('St. Paul and Sts Peter and Paul')).toBe('Saint Paul and Saints Peter and Paul')
  })

  it('splits long text into pieces of about 200 characters at sentence ends', () => {
    const sentence = 'Lord, have mercy on us all. ' // 28 characters
    const chunks = toChunks(sentence.repeat(20))
    expect(chunks.length).toBeGreaterThan(1)
    for (const chunk of chunks) {
      expect(chunk.length).toBeLessThanOrEqual(200)
      expect(chunk.endsWith('.')).toBe(true)
    }
  })

  // A pretend voice: only the fields offeredVoices looks at.
  const voice = (name: string, lang = 'en-US', extra: Partial<SpeechSynthesisVoice> = {}) =>
    ({ name, lang, localService: true, default: false, voiceURI: name, ...extra }) as SpeechSynthesisVoice

  it('offers only David, Mark and Zira on Windows', () => {
    const windows = [
      voice('Microsoft Zira - English (United States)'),
      voice('Microsoft George - English (United Kingdom)', 'en-GB'),
      voice('Microsoft David - English (United States)', 'en-US', { default: true }),
      voice('Microsoft Hazel - English (United Kingdom)', 'en-GB'),
      voice('Microsoft Mark - English (United States)'),
      voice('Microsoft Aria Online (Natural) - English (United States)', 'en-US', { localService: false }),
    ]
    const offered = offeredVoices(windows)
    expect(offered.map((v) => v.name)).toEqual([
      'Microsoft David - English (United States)',
      'Microsoft Mark - English (United States)',
      'Microsoft Zira - English (United States)',
    ])
    expect(offered.map(voiceLabel)).toEqual(['David', 'Mark', 'Zira'])
  })

  it('offers three American voices on a new iPhone, which has only Samantha and synthetic ones', () => {
    const iphone = [
      voice('Bubbles'),
      voice('Daniel', 'en-GB'),
      voice('Eddy (English (US))'),
      voice('Fred'),
      voice('Reed (English (UK))', 'en-GB'),
      voice('Reed (English (US))'),
      voice('Samantha', 'en-US', { default: true }),
      voice('Sandy (English (US))'),
      voice('Shelley (English (US))'),
      voice('Thomas', 'fr-FR'),
      voice('Zarvox'),
    ]
    expect(offeredVoices(iphone).map(voiceLabel)).toEqual(['Samantha', 'Reed', 'Shelley'])
  })

  it('puts downloaded "Premium" and "Enhanced" voices first, each name once', () => {
    const iphone = [
      voice('Samantha'),
      voice('Samantha (Enhanced)'),
      voice('Reed (English (US))'),
      voice('Ava (Premium)'),
    ]
    expect(offeredVoices(iphone).map((v) => v.name)).toEqual(['Ava (Premium)', 'Samantha (Enhanced)', 'Reed (English (US))'])
  })

  it('never offers online voices or ones that are not American', () => {
    const others = [
      voice('Google US English', 'en-US', { localService: false }),
      voice('Karen', 'en-AU'),
      voice('Moira', 'en-IE'),
    ]
    expect(offeredVoices(others)).toEqual([])
  })
})

describe('scripture', () => {
  it('removes the translators’ brackets for speaking', () => {
    expect(plainScripture('And thou givest [them] their food')).toBe('And thou givest them their food')
  })

  it('starts paragraphs where they are marked', () => {
    const verses = [
      { number: '1', paragraphStart: true },
      { number: '2', paragraphStart: false },
      { number: '3', paragraphStart: true },
    ]
    expect(toParagraphs(verses).map((p) => p.length)).toEqual([2, 1])
  })

  it('puts each verse on its own line when nothing is marked (the Septuagint)', () => {
    const verses = [
      { number: '1', paragraphStart: false },
      { number: '2', paragraphStart: false },
    ]
    expect(toParagraphs(verses).length).toBe(2)
  })
})

describe('prayer book', () => {
  const book: PrayerBook = {
    title: 'Prayer Book',
    about: '',
    sections: [
      { id: 'a', title: 'Beginning Prayers', items: [{ lines: ['Glory to Thee'] }] },
      { id: 'b', title: 'Morning', items: [{ include: 'a' }, { title: 'Psalm', lines: ['…'] }] },
      { id: 'loop', title: 'Loop', items: [{ include: 'loop' }] },
    ],
  }

  it('includes other sections, titled with the section’s name', () => {
    const items = expandItems(book, book.sections[1].items)
    expect(items.map((i) => i.title)).toEqual(['Beginning Prayers', 'Psalm'])
  })

  it('never includes a section inside itself forever', () => {
    expect(expandItems(book, book.sections[2].items)).toEqual([])
  })

  it('reads "three times" prayers three times and skips instructions', () => {
    expect(spokenLine({ text: 'Lord, have mercy.', times: 3 })).toHaveLength(3)
    expect(spokenLine({ rubric: 'Prostration' })).toEqual([])
    expect(timesLabel(3)).toBe('Three times')
  })
})

describe('the Church year (Liturgical seasons style)', () => {
  it('finds Orthodox Pascha', () => {
    // Checked against orthocal.info, which counts days from Pascha.
    expect(orthodoxPascha(2026)).toBe('2026-04-12')
    expect(orthodoxPascha(2027)).toBe('2027-05-02')
    expect(orthodoxPascha(2025)).toBe('2025-04-20') // the year Pascha fell on the same day for everyone
  })

  it('counts days from Pascha like Orthocal does', () => {
    expect(daysFromPascha('2026-10-23')).toBe(194)
    expect(daysFromPascha('2027-04-25')).toBe(-7) // Palm Sunday
  })

  it('picks the season and vestment color', () => {
    expect(liturgicalSeason('2027-03-31').id).toBe('lent')
    expect(liturgicalSeason('2027-04-25').id).toBe('palms')
    expect(liturgicalSeason('2027-04-30').id).toBe('holy-week')
    expect(liturgicalSeason('2027-05-02')).toMatchObject({ id: 'pascha', color: 'red' })
    expect(liturgicalSeason('2027-06-20').id).toBe('pentecost') // Pentecost: Pascha + 49
    expect(liturgicalSeason('2027-03-25').id).toBe('theotokos') // the Annunciation, even in Lent
    expect(liturgicalSeason('2026-12-25').id).toBe('nativity')
    expect(liturgicalSeason('2027-01-06').id).toBe('theophany')
    expect(liturgicalSeason('2026-09-14').id).toBe('cross')
    expect(liturgicalSeason('2026-10-23')).toMatchObject({ id: 'ordinary', color: 'gold' })
  })
})

describe('phone numbers', () => {
  it('shows US numbers the usual way, and others as saved', () => {
    expect(phoneText('+12145550123')).toBe('(214) 555-0123')
    expect(phoneText('+442079460958')).toBe('+442079460958')
  })
})

describe('tabs', () => {
  it('knows which tab each address belongs to', () => {
    expect(tabFor('/')).toBe('home')
    expect(tabFor('/bible/PSA/50')).toBe('bible')
    expect(tabFor('/prayers')).toBe('prayers')
    expect(tabFor('/church/dioceses/wichita')).toBe('church')
    expect(tabFor('/sign-in')).toBe('profile')
    expect(tabFor('/profile/members')).toBe('profile')
    expect(tabFor('/biblestudy')).toBeNull() // not /bible
    expect(tabFor('/nowhere')).toBeNull()
  })
})

describe('the daily prayer intention', () => {
  const data = church as ChurchData

  it('includes the Patriarch, the Metropolitan, every diocese and every ministry', () => {
    expect(allIntentions(data)).toHaveLength(2 + data.dioceses.length + data.ministries.length)
  })

  it('changes every day, the same for everyone, and comes round again', () => {
    const n = allIntentions(data).length
    expect(intentionFor('2026-10-23', data)).toEqual(intentionFor('2026-10-23', data))
    expect(intentionFor('2026-10-24', data)).not.toEqual(intentionFor('2026-10-23', data))
    expect(intentionFor(addDays('2026-10-23', n), data)).toEqual(intentionFor('2026-10-23', data))
  })

  it('prays for each diocese with its shepherd', () => {
    const wichita = allIntentions(data).find((i) => i.to === '/church/dioceses/wichita')
    expect(wichita?.prayer).toBe(
      'For Archimandrite CALINIC (Berger), and for the clergy and faithful of the Diocese of Wichita and Mid-America.',
    )
  })
})
