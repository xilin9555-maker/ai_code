<script setup lang="ts">
import { CheckCircleOutlined, CopyOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'

const props = defineProps<{ deployedUrl: string }>()
const open = defineModel<boolean>('open', { default: false })

/** 复制部署地址，方便用户把生成的网站分享给其他人。 */
async function copyDeployedUrl() {
  if (!props.deployedUrl) return
  try {
    await navigator.clipboard.writeText(props.deployedUrl)
    message.success('部署地址已复制')
  } catch {
    message.error('复制失败，请手动选择地址复制')
  }
}

/** 只有用户明确点击访问按钮后，才在新标签页打开部署结果。 */
function visitDeployedSite() {
  if (!props.deployedUrl) return
  window.open(props.deployedUrl, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <Modal v-model:open="open" title="部署成功" :footer="null" width="560px">
    <div class="deploy-success">
      <CheckCircleOutlined class="deploy-success-icon" />
      <h2>网站部署成功！</h2>
      <p>你的网站已经完成部署，可以通过下面的地址访问或分享。</p>
      <div class="deploy-url-box">
        <span :title="deployedUrl">{{ deployedUrl }}</span>
        <a-button type="text" aria-label="复制部署地址" @click="copyDeployedUrl">
          <CopyOutlined />
        </a-button>
      </div>
      <div class="deploy-success-actions">
        <a-button type="primary" @click="visitDeployedSite">访问网站</a-button>
        <a-button @click="open = false">关闭</a-button>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.deploy-success {
  padding: 16px 8px 4px;
  text-align: center;
}

.deploy-success-icon {
  color: #52c41a;
  font-size: 48px;
}

.deploy-success h2 {
  margin: 17px 0 8px;
  color: var(--ink);
  font-size: 20px;
}

.deploy-success > p {
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 12px;
}

.deploy-url-box {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 5px 7px 5px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #faf9f5;
  text-align: left;
}

.deploy-url-box span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: #55564e;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deploy-success-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 22px;
}
</style>
