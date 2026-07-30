<template>
  <footer class="app-footer">
    <div class="app-footer__inner page-shell">
      <div>
        <strong>DevNexus AI</strong>
        <p>一个用于学习可靠消息、微服务治理与现代 RAG 的个人工程项目。</p>
      </div>
      <div class="app-footer__links">
        <RouterLink to="/about">项目介绍</RouterLink>
        <RouterLink to="/architecture">系统架构</RouterLink>
        <RouterLink to="/evidence">工程证据</RouterLink>
        <RouterLink to="/chat">AI 助手</RouterLink>
        <a
          href="https://github.com/vanillahusk/devnexus-ai"
          target="_blank"
          rel="noreferrer"
        >
          GitHub
        </a>
      </div>
      <small>© 2026 DevNexus AI · Built with evidence, not buzzwords.</small>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineOptions({
  name: 'AppFooter'
})
</script>

<style scoped>
.app-footer {
  margin-top: var(--space-16);
  border-top: 1px solid var(--color-border-subtle);
  background: rgb(255 255 255 / 72%);
}

.app-footer__inner {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-6);
  align-items: end;
  padding-block: var(--space-10);
}

strong {
  color: var(--color-text);
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 750;
}

p,
small {
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

p {
  margin-top: var(--space-2);
}

small {
  grid-column: 1 / -1;
}

.app-footer__links {
  display: flex;
  gap: var(--space-5);
}

.app-footer__links a {
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  font-weight: 650;
  text-decoration: none;
}

.app-footer__links a:hover {
  color: var(--color-brand-strong);
}

@media (max-width: 640px) {
  .app-footer__inner {
    grid-template-columns: 1fr;
  }

  .app-footer__links {
    flex-wrap: wrap;
  }
}
</style>
