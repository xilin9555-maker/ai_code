/** 后端支持的代码生成类型，值需要与数据库和接口枚举保持一致。 */
export enum CodeGenTypeEnum {
  HTML = 'html',
  MULTI_FILE = 'multi_file',
  VUE_PROJECT = 'vue_project',
}

/** 每种生成类型的界面名称，列表、详情和筛选框可以共用同一份配置。 */
export const CODE_GEN_TYPE_CONFIG = {
  [CodeGenTypeEnum.HTML]: {
    label: 'HTML 单文件',
    value: CodeGenTypeEnum.HTML,
  },
  [CodeGenTypeEnum.MULTI_FILE]: {
    label: 'HTML 多文件',
    value: CodeGenTypeEnum.MULTI_FILE,
  },
  [CodeGenTypeEnum.VUE_PROJECT]: {
    label: 'Vue 工程',
    value: CodeGenTypeEnum.VUE_PROJECT,
  },
} as const

export const CODE_GEN_TYPE_OPTIONS = Object.values(CODE_GEN_TYPE_CONFIG)

export function getCodeGenTypeLabel(value?: string) {
  if (!value) return '未知方式'
  return CODE_GEN_TYPE_CONFIG[value as CodeGenTypeEnum]?.label ?? value
}
