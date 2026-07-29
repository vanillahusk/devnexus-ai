<template>
  <article class="metric-card">
    <p class="metric-card__value">{{ value }}</p>
    <p class="metric-card__label">{{ label }}</p>
    <p class="metric-card__note">{{ note }}</p>
  </article>
</template>

<script setup lang="ts">
defineOptions({
  name: 'MetricCard'
})

defineProps<{
  value: string
  label: string
  note: string
}>()
</script>

<style scoped>
.metric-card {
  min-width: 0;
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--color-surface) 92%, transparent);
  box-shadow: var(--shadow-sm);
}

.metric-card__value {
  color: var(--color-brand-strong);
  font-family: var(--font-display);
  font-size: clamp(1.55rem, 3vw, 2.15rem);
  font-weight: 750;
  letter-spacing: -0.04em;
  line-height: 1;
}

.metric-card__label {
  margin-top: var(--space-3);
  color: var(--color-text);
  font-size: 0.92rem;
  font-weight: 650;
}

.metric-card__note {
  margin-top: var(--space-2);
  color: var(--color-text-muted);
  font-size: 0.78rem;
  line-height: 1.5;
}
</style>
