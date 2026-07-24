<template>
  <div class="ai-assistant-box bg-color-white">
    <div class="assistant-head">
      <div>
        <h4>AI 知识助手</h4>
        <p>结合社区规则、FAQ、文章内容和评论上下文，帮你快速定位答案。</p>
      </div>
      <div class="assistant-tags" v-if="reply.answer">
        <el-tag size="small" type="success">{{ reply.route }}</el-tag>
        <el-tag v-if="reply.degraded" size="small" type="warning">已降级</el-tag>
      </div>
    </div>

    <el-input
      v-model="question"
      type="textarea"
      resize="none"
      :rows="3"
      maxlength="300"
      :placeholder="isLogin ? '例如：这篇文章的评论异步链路为什么要加 processing 队列？' : '登录后可以向 AI 知识助手提问'"
      @click="handleFocus"
    />

    <div class="assistant-action">
      <el-button type="primary" :loading="loading" :disabled="!question.trim()" @click="askAssistant">
        提问
      </el-button>
      <span class="assistant-tip" v-if="reply.degraded && reply.degradeReason">
        {{ reply.degradeReason }}
      </span>
    </div>

    <div v-if="reply.answer" class="assistant-answer">
      <h5>回答</h5>
      <p class="answer-text">{{ reply.answer }}</p>

      <div v-if="reply.references.length > 0" class="assistant-ref">
        <h5>命中资料</h5>
        <div class="ref-item" v-for="(refItem, index) in reply.references" :key="index">
          <strong>{{ refItem.title }}</strong>
          <span class="ref-type">{{ refItem.sourceType }}</span>
          <p>{{ refItem.snippet }}</p>
        </div>
      </div>

      <div v-if="reply.history.length > 1" class="assistant-history">
        <h5>最近追问</h5>
        <div class="history-item" v-for="(historyItem, index) in reply.history.slice(0, 3)" :key="index">
          <p class="history-question">Q: {{ historyItem.question }}</p>
          <p class="history-answer">A: {{ historyItem.answer }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { inject, reactive, ref } from 'vue'
import { doPost } from '@/http/BackendRequests'
import type { CommonResponse } from '@/http/ResponseTypes/CommonResponseType'
import { AI_ASSISTANT_ASK_URL } from '@/http/URL'
import { defaultAiAssistantReply, type AiAssistantReply } from '@/http/ResponseTypes/AiAssistantResponseType'
import { messageTip } from '@/util/utils'

const props = defineProps<{
  articleId: number;
  isLogin: boolean;
}>()

const showLoginDialog = inject<() => void>('loginDialogClicked')

const question = ref('')
const loading = ref(false)
const sessionId = ref('')
const reply = reactive<AiAssistantReply>({ ...defaultAiAssistantReply })

const handleFocus = () => {
  if (!props.isLogin && showLoginDialog) {
    showLoginDialog()
  }
}

const askAssistant = async () => {
  if (!props.isLogin) {
    handleFocus()
    return
  }
  loading.value = true
  try {
    const response = await doPost<CommonResponse<AiAssistantReply>>(AI_ASSISTANT_ASK_URL, {
      articleId: props.articleId,
      question: question.value,
      sessionId: sessionId.value,
      includeComments: true,
    })
    Object.assign(reply, response.data.result)
    sessionId.value = response.data.result.sessionId
  } catch (error) {
    messageTip('AI 助手暂时不可用，请稍后重试', 'error')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.ai-assistant-box {
  margin: 20px 0;
  padding: 24px;
  border-radius: 14px;
  border: 1px solid #ebeef5;
}

.assistant-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.assistant-head h4 {
  margin: 0 0 6px;
  font-size: 18px;
}

.assistant-head p {
  margin: 0;
  color: #666;
  line-height: 1.6;
}

.assistant-tags {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.assistant-action {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.assistant-tip {
  color: #c27b00;
  font-size: 13px;
}

.assistant-answer {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px dashed #e5e7eb;
}

.assistant-answer h5 {
  margin: 0 0 10px;
  font-size: 15px;
}

.answer-text {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #333;
}

.assistant-ref,
.assistant-history {
  margin-top: 18px;
}

.ref-item,
.history-item {
  padding: 12px 14px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #f8fafc;
}

.ref-type {
  margin-left: 8px;
  color: #7c8aa5;
  font-size: 12px;
}

.ref-item p,
.history-item p {
  margin: 6px 0 0;
  line-height: 1.7;
}

.history-question {
  font-weight: 600;
}

@media (max-width: 768px) {
  .ai-assistant-box {
    padding: 18px;
  }

  .assistant-head {
    flex-direction: column;
  }
}
</style>
