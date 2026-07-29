/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_APP_NAME?: string
  readonly VITE_GITHUB_URL?: string
  readonly VITE_COMMUNITY_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
