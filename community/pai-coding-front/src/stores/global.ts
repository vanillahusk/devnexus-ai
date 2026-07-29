// src/stores/global.ts
import { defineStore } from 'pinia'
import { GLOBAL_STORE } from '@/constants/StoreConstants'
import { defaultGlobalResponse, type GlobalResponse } from '@/http/ResponseTypes/CommonResponseType'

export const useGlobalStore = defineStore(GLOBAL_STORE, {
  state: () => ({
    global: {... defaultGlobalResponse} as GlobalResponse,
    initialized: false,
    loading: false
  }),
  actions: {
    setGlobal(globalResponse: GlobalResponse) {
      Object.assign(this.global, globalResponse)
      this.initialized = true
    },
    setLoading(loading: boolean) {
      this.loading = loading
    },
    markInitialized() {
      this.initialized = true
    },
  },
})
