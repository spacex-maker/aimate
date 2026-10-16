/** AIMATEX Account OIDC + PKCE helpers (browser only). */

const SS_STATE = 'aimatex_oidc_state'
const SS_VERIFIER = 'aimatex_oidc_verifier'
const SS_REDIRECT = 'aimatex_oidc_redirect'
const SS_NEXT = 'aimatex_oidc_next'

export type OidcPublicConfig = {
  enabled: boolean
  clientId: string
  authorizeUrl: string
  scopes: string
  issuer: string
  redirectUri: string
  redirectUris: string[]
}

function toBase64Url(bytes: ArrayBuffer | Uint8Array): string {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  let bin = ''
  for (let i = 0; i < arr.length; i++) {
    bin += String.fromCharCode(arr[i]!)
  }
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function randomUrlSafe(bytes = 32): string {
  const buf = new Uint8Array(bytes)
  crypto.getRandomValues(buf)
  return toBase64Url(buf)
}

async function sha256Base64Url(plain: string): Promise<string> {
  const data = new TextEncoder().encode(plain)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return toBase64Url(digest)
}

export function clearOidcSession(): void {
  sessionStorage.removeItem(SS_STATE)
  sessionStorage.removeItem(SS_VERIFIER)
  sessionStorage.removeItem(SS_REDIRECT)
  sessionStorage.removeItem(SS_NEXT)
}

export async function beginAimatexLogin(
  config: OidcPublicConfig,
  nextPath?: string | null,
): Promise<void> {
  if (!config.enabled) {
    throw new Error('统一登录未启用')
  }
  const state = randomUrlSafe(24)
  const verifier = randomUrlSafe(48)
  const challenge = await sha256Base64Url(verifier)
  const redirectUri = config.redirectUri

  sessionStorage.setItem(SS_STATE, state)
  sessionStorage.setItem(SS_VERIFIER, verifier)
  sessionStorage.setItem(SS_REDIRECT, redirectUri)
  if (nextPath && nextPath.startsWith('/') && !nextPath.startsWith('//')) {
    sessionStorage.setItem(SS_NEXT, nextPath)
  } else {
    sessionStorage.removeItem(SS_NEXT)
  }

  const url = new URL(config.authorizeUrl)
  url.searchParams.set('client_id', config.clientId)
  url.searchParams.set('redirect_uri', redirectUri)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('scope', config.scopes)
  url.searchParams.set('state', state)
  url.searchParams.set('code_challenge', challenge)
  url.searchParams.set('code_challenge_method', 'S256')
  window.location.assign(url.toString())
}

export function takeOidcCallbackParams(search: string): {
  code: string
  state: string
  codeVerifier: string
  redirectUri: string
  next: string | null
} {
  const params = new URLSearchParams(search)
  const err = params.get('error')
  if (err) {
    const desc = params.get('error_description') || err
    clearOidcSession()
    throw new Error(desc)
  }
  const code = params.get('code')
  const state = params.get('state')
  const expected = sessionStorage.getItem(SS_STATE)
  const verifier = sessionStorage.getItem(SS_VERIFIER)
  const redirectUri = sessionStorage.getItem(SS_REDIRECT)
  const next = sessionStorage.getItem(SS_NEXT)
  if (!code || !state || !expected || state !== expected) {
    clearOidcSession()
    throw new Error('登录状态校验失败，请重试')
  }
  if (!verifier || !redirectUri) {
    clearOidcSession()
    throw new Error('登录会话已失效，请重新登录')
  }
  clearOidcSession()
  return { code, state, codeVerifier: verifier, redirectUri, next }
}
