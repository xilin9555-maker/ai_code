export default {
  requestLibPath: "import request from '@/request'",
  requestOptionsType: "import('axios').AxiosRequestConfig",
  schemaPath: process.env.OPENAPI_SCHEMA_URL || 'http://localhost:8101/api/v3/api-docs',
  serversPath: './src',
}
