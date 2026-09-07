# 灵构 · AI 应用工坊

按第 2 章前端初始化的功能范围搭建，采用独立名称、Logo 和暖白 / 墨黑 / 橙色界面。前端位于后端项目内，沿用同一个 Git 仓库。

## 启动

当前已验证环境：Node.js 24.14.0、npm 11.9.0。使用 create-vue 3.17.0 初始化，依赖的实际安装版本由 package-lock.json 锁定。

1. 在 IDEA 启动后端 `AiCodeApplication`，默认端口为 8101。
2. 在当前前端目录运行：

```powershell
npm ci
npm run dev
```

打开 http://127.0.0.1:5173 。保持使用同一个地址：localhost 与 127.0.0.1 的浏览器草稿存储彼此独立。

默认浏览器请求 `/api`，由 Vite 代理到 `http://localhost:8101`。需要修改时，将 `.env.example` 复制为 `.env.local`，修改后重新启动 Vite。生产环境需要将 `/api` 反向代理到后端，并为前端路由配置回退到 `index.html`；也可以构建时设置 `VITE_API_BASE_URL` 为后端地址。

## 已实现的范围

- Vue 3 Composition API、TypeScript、Vite、Vue Router、Pinia。
- Ant Design Vue 4.2.6，仅注册使用到的组件，统一中文语言和主题。
- 公共页头、内容区、页脚；桌面和手机布局；跳转到内容和键盘焦点样式。
- 创作空间、我的草稿、关于页面、登录说明页及 404 页面。
- 灵感示例填入、草稿保存 / 更新 / 删除；草稿保存在当前浏览器 localStorage。
- Axios 统一请求、60 秒超时、携带 Cookie、业务错误拒绝和未登录跳转。
- OpenAPI 生成接口函数与 TypeScript 类型；后端健康检查展示及重试。
- ESLint、Prettier、类型检查、生产构建。

登录页是入口说明，尚无登录接口。灵感卡片是设计示意，保存的是需求草稿；AI 生成、预览、部署和云端数据将在后续模块接入。

## 实现思路

```text
src/
  api/                  从后端 OpenAPI 自动生成，不手动修改
  assets/styles.css     主题、布局和响应式样式
  components/           页头、页脚、灵感卡片
  data/inspirations.ts  灵感示例数据
  layouts/              BasicLayout 公共布局
  pages/                按路由拆分的页面
  router/               路由、菜单元信息、标题及滚动行为
  stores/               本地草稿状态和后端连接状态
  request.ts            Axios 统一请求入口
```

页面负责交互和展示，Pinia 负责共享状态，请求层负责访问后端。以后接入应用模块时，可以将草稿的本地存储替换为服务端接口，而不需要重写公共布局。

菜单从路由 `meta.inMenu` 生成，并通过当前 `route.path` 计算选中项，点击、浏览器前进后退或刷新都会同步。关于、草稿等页面采用懒加载。

页脚通过 Flex 布局保持短页面靠底、长页面随内容延伸，不使用固定定位遮挡内容。

健康检查由 `stores/service.ts` 调用实际生成的 `healthCheck` 接口，验证业务码和响应数据后展示连接状态。失败时允许重试，不伪造服务在线状态。

`request.ts` 保留 AxiosResponse 结构，页面通过 `response.data` 获取后端统一响应。业务错误将拒绝 Promise；40100 跳转到登录入口并编码当前相对路径，避免重定向循环。普通网络错误由调用方根据场景展示，连接检测采用页脚状态反馈。

草稿保存前校验长度，先完成 localStorage 写入，再更新页面并提示成功；空输入不可保存，删除需要确认。草稿目前仅在本机浏览器保存，清除浏览器数据会一并清除。

Logo 和灵感缩略图使用本地 SVG / HTML / CSS，页面无需请求外部图片或字体。

## 重新生成接口

先确保后端启动，再执行：

```powershell
npm run openapi2ts
```

配置在 `openapi2ts.config.ts`，默认读取 `http://localhost:8101/api/v3/api-docs`。可以用 `OPENAPI_SCHEMA_URL` 环境变量覆盖。生成代码使用项目内 Axios 实例，配置中明确了 AxiosRequestConfig 类型。

```powershell
$env:OPENAPI_SCHEMA_URL = 'http://localhost:8101/api/v3/api-docs'
npm run openapi2ts
```

提交生成结果，让新环境在后端尚未启动时也能编译前端。生成文件由工具维护，ESLint 忽略该目录；类型检查仍然覆盖它。

## 检查命令

```powershell
npm run lint
npm run format:check
npm run build
```

修复代码格式使用 `npm run format`，自动修复代码规范使用 `npm run lint:fix`。`npm run preview` 仅用于预览构建产物，不自带开发代理；完整联调使用 `npm run dev` 或配置生产反向代理。

浏览器验收：空描述不能保存；选择灵感后可保存；草稿刷新后仍存在；继续编辑更新同一份草稿；删除确认后恢复空状态；关于页面刷新后菜单仍高亮；手机端无横向滚动；页脚健康状态来自真实后端。

## 当前依赖说明

沿用教程的 `@umijs/openapi` 作为开发期代码生成工具。安装时 npm audit 报告其传递依赖 mockjs 存在原型污染告警，当前没有自动修复版本。它用于本地代码生成，不在浏览器运行依赖中；仅从可信后端生成接口，后续可评估更换生成器。没有用强制降级或关闭检查的方式掩盖该告警。
