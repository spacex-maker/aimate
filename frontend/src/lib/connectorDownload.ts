/** Public download URLs for Aimate Connector (hosted under chat web root). */
export const CONNECTOR_VERSION = '0.1.0'

export const CONNECTOR_DOWNLOAD = {
  pagePath: '/download',
  /** Stable latest Windows installer */
  windowsSetupUrl: '/downloads/Aimate-Connector-setup.exe',
  /** Versioned installer (same file, kept for reference) */
  windowsSetupVersionedUrl: `/downloads/Aimate-Connector-${CONNECTOR_VERSION}-setup.exe`,
} as const