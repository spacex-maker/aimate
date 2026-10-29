/** Open user center globally (Navbar listens). */

export type UserCenterTab = 'overview' | 'profile' | 'recharge' | 'consume' | 'orders'

export const OPEN_USER_CENTER_EVENT = 'aimate:open-user-center'

export function openUserCenter(tab: UserCenterTab = 'overview') {
  if (typeof window === 'undefined') return
  window.dispatchEvent(
    new CustomEvent(OPEN_USER_CENTER_EVENT, { detail: { tab } }),
  )
}
