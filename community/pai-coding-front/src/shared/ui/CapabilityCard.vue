<template>
  <article class="capability-card">
    <div class="capability-card__icon" aria-hidden="true">{{ icon }}</div>
    <p class="capability-card__eyebrow">{{ eyebrow }}</p>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <ul>
      <li v-for="item in items" :key="item">{{ item }}</li>
    </ul>
  </article>
</template>

<script setup lang="ts">
defineOptions({
  name: 'CapabilityCard'
})

defineProps<{
  icon: string
  eyebrow: string
  title: string
  description: string
  items: string[]
}>()
</script>

<style scoped>
.capability-card {
  position: relative;
  overflow: hidden;
  padding: var(--space-6);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.capability-card::after {
  position: absolute;
  right: -4rem;
  bottom: -5rem;
  width: 10rem;
  height: 10rem;
  border-radius: 999px;
  background: var(--color-brand-soft);
  content: '';
  filter: blur(12px);
  opacity: 0.8;
  pointer-events: none;
}

.capability-card__icon {
  display: grid;
  width: 2.8rem;
  height: 2.8rem;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  font-size: 1.25rem;
}

.capability-card__eyebrow {
  margin-top: var(--space-5);
  color: var(--color-brand-strong) !important;
  font-size: 0.72rem !important;
  font-weight: 750 !important;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h3 {
  margin-top: var(--space-2);
  color: var(--color-text);
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-weight: 720;
}

p {
  position: relative;
  z-index: 1;
  margin-top: var(--space-3);
  color: var(--color-text-muted);
  font-size: 0.9rem;
  line-height: 1.7;
}

ul {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

li {
  padding: 0.35rem 0.65rem;
  border: 1px solid var(--color-border-subtle);
  border-radius: 999px;
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
  font-size: 0.72rem;
  font-weight: 600;
}
</style>
