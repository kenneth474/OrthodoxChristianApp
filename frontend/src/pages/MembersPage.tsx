/*
  MembersPage ("/profile/members"): everyone who has an account, for the
  app's administrators (see admin/Admins.java). Opened from the Profile tab.

  Shows how many accounts there are, and each member's name, email, phone
  number, how they sign in and when they joined, newest first. Passwords
  are never shown: the app doesn't know them (it keeps only a one-way
  scramble, a "hash"). "Download" saves the list as a CSV file, which
  Excel, Numbers and Google Sheets all open.
*/
import { Link } from 'react-router'
import { Masthead } from '../components/ornaments'
import { useApiData } from '../lib/data'
import { phoneText } from '../lib/phone'
import type { Member, Members } from '../types'

export function MembersPage() {
  const { data, error } = useApiData<Members>('/api/admin/members')

  return (
    <div className="screen">
      <Masthead
        eyebrow="For administrators"
        title="Members"
        subtitle={data ? `${data.count} ${data.count === 1 ? 'account' : 'accounts'}` : 'Everyone with an account'}
      />
      <main className="page narrow">
        <Link className="back" to="/profile">
          ‹ Profile
        </Link>
        {error && <p className="message error">{error}</p>}
        {!data && !error && <p className="status">Loading…</p>}
        {data && (
          <>
            <p className="hint">
              Passwords are never shown, to anyone: the app only keeps a scrambled check of each one, which can't be turned
              back into the password.
            </p>
            <button type="button" onClick={() => download(data.members)}>
              Download as a spreadsheet (CSV)
            </button>
            <ul className="members">
              {data.members.map((member) => (
                <li key={member.email} className="card">
                  <span className="member-name">{member.name}</span>
                  <a href={`mailto:${member.email}`}>{member.email}</a>
                  {member.phoneNumber && <a href={`tel:${member.phoneNumber}`}>{phoneText(member.phoneNumber)}</a>}
                  <span className="hint">
                    {signInText(member)} · joined {dateText(member.joined)}
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </main>
    </div>
  )
}

function signInText(member: Member): string {
  const ways = [member.password && 'password', member.google && 'Google'].filter(Boolean)
  return ways.length ? `Signs in with ${ways.join(' and ')}` : 'No way to sign in'
}

/** "2026-10-24T14:03:00Z" -> "Oct 24, 2026" */
function dateText(instant: string): string {
  return new Date(instant).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

/**
 * Saves the list as members.csv: one line per member, the values separated
 * by commas, each in "quotes" (with any " inside doubled) so commas in names
 * don't split them.
 */
function download(members: Member[]) {
  const quote = (value: string) => `"${value.replaceAll('"', '""')}"`
  const lines = [
    ['Name', 'Email', 'Phone', 'Signs in with', 'Joined'],
    ...members.map((m) => [m.name, m.email, m.phoneNumber ?? '', signInText(m).replace('Signs in with ', ''), m.joined]),
  ].map((row) => row.map(quote).join(','))
  const file = new Blob([lines.join('\r\n')], { type: 'text/csv' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(file)
  link.download = 'members.csv'
  link.click()
  URL.revokeObjectURL(link.href)
}
