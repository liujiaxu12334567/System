const BASE_URL = '/api'

const buildHeaders = () => {
  const headers = {
    'Content-Type': 'application/json'
  }
  const token = localStorage.getItem('token')
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

export async function streamAiChat(payload, onChunk, options = {}) {
  const controller = new AbortController()
  const signal = options.signal
  if (signal) {
    signal.addEventListener('abort', () => controller.abort())
  }

  const response = await fetch(`${BASE_URL}/ai/chat`, {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify(payload),
    signal: controller.signal
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => 'AI 服务返回异常')
    throw new Error(errorText || 'AI 服务异常')
  }

  if (!response.body) {
    const text = await response.text()
    if (text && typeof onChunk === 'function') {
      onChunk(text)
    }
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      if (chunk && typeof onChunk === 'function') {
        onChunk(chunk)
      }
    }
  } finally {
    reader.releaseLock()
  }
}
