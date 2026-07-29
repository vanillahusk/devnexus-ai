function clean(value: string | undefined, fallback: string): string {
  const normalized = value?.trim()
  return normalized || fallback
}

export const runtimeConfig = Object.freeze({
  appName: clean(import.meta.env.VITE_APP_NAME, 'DevNexus Console'),
  githubUrl: clean(
    import.meta.env.VITE_GITHUB_URL,
    'https://github.com/vanillahusk/devnexus-ai'
  ),
  communityUrl: clean(import.meta.env.VITE_COMMUNITY_URL, '/')
})
