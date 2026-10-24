/*
  ProfileTab: what the Profile tab shows. At /profile or /sign-in: your
  profile when you're signed in, the sign-in form when you're not. At
  /profile/members: the Members page, for administrators.

  Deciding here, instead of each page sending you to the other one with a
  redirect, matters because tabs stay alive in the background (TabsHost):
  a redirect remembered by a hidden page would fire again later, at the
  wrong moment. Signing in or out now simply changes what this shows.
*/
import { useAccount } from '../account-context'
import { MembersPage } from './MembersPage'
import { ProfilePage } from './ProfilePage'
import { SignInPage } from './SignInPage'

export function ProfileTab({ page = 'profile' }: { page?: 'profile' | 'members' }) {
  const { account, loading } = useAccount()
  if (loading) {
    return (
      <main className="page narrow">
        <p className="status">Loading…</p>
      </main>
    )
  }
  if (!account) return <SignInPage />
  // Not an administrator? Then there's no Members page to see: show the profile.
  return page === 'members' && account.admin ? <MembersPage /> : <ProfilePage />
}
