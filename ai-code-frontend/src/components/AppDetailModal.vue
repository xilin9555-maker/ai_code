<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined, EditOutlined, UserOutlined } from '@ant-design/icons-vue'
import { Descriptions, Modal } from 'ant-design-vue'
import { getCodeGenTypeLabel } from '@/constants/codeGenType'
import { formatAppDate, getAppCoverUrl, type AppView } from '@/utils/app'

const props = withDefaults(
  defineProps<{
    app?: AppView
    canManage?: boolean
    deleting?: boolean
  }>(),
  {
    canManage: false,
    deleting: false,
  },
)

defineEmits<{
  edit: []
  delete: []
}>()

const open = defineModel<boolean>('open', { default: false })
const coverUrl = computed(() => (props.app ? getAppCoverUrl(props.app, 720, 360) : ''))
</script>

<template>
  <Modal v-model:open="open" title="应用详情" :footer="null">
    <img
      v-if="coverUrl"
      class="detail-cover"
      :src="coverUrl"
      :alt="`${app?.appName || '应用'}封面`"
    />
    <Descriptions :column="1" bordered size="small">
      <Descriptions.Item label="应用 ID">{{ app?.id || '—' }}</Descriptions.Item>
      <Descriptions.Item label="生成方式">
        {{ getCodeGenTypeLabel(app?.codeGenType) }}
      </Descriptions.Item>
      <Descriptions.Item label="创建者">
        <span class="creator-info">
          <a-avatar :size="28" :src="app?.user?.userAvatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          {{ app?.user?.userName || app?.user?.userAccount || '—' }}
        </span>
      </Descriptions.Item>
      <Descriptions.Item label="创建时间">{{ formatAppDate(app?.createTime) }}</Descriptions.Item>
      <Descriptions.Item label="初始需求">{{ app?.initPrompt || '—' }}</Descriptions.Item>
    </Descriptions>
    <div v-if="canManage" class="detail-actions">
      <a-button @click="$emit('edit')"><EditOutlined /> 修改</a-button>
      <a-button danger :loading="deleting" @click="$emit('delete')">
        <DeleteOutlined /> 删除
      </a-button>
    </div>
  </Modal>
</template>

<style scoped>
.detail-cover {
  display: block;
  width: 100%;
  height: 180px;
  margin-bottom: 16px;
  object-fit: cover;
  border-radius: 10px;
  background: #e9e6dc;
}

.creator-info {
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}
</style>
