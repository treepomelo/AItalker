# 前端运行与环境配置

当前通过 HBuilderX 的 Vue 3 / Uni-app 工具链编译，打开本目录后运行到微信开发者工具。

## 页面范围

- 启动页为 `pages/product/product`，底部导航包含商品讲解、`pages/chat/chat` 实时聊天、`pages/settings/models` 模型配置三个页面。
- 模型配置通过后端管理接口保存，密钥不进入前端存储；本机 local 模式允许配置，远程访问需 ADMIN 登录。
- 旧 AI 聊天、语音演示、登录、个人中心页面未注册，不进入当前小程序页面包。
- 旧聊天源码暂留，但不提供入口，也不新增匿名 WebSocket 通道。现有后端聊天仍要求有效登录 Token。
- 当前页面不引用文件选择、文件上传、图片上传和登录组件。

## 后端地址

`env.js` 从 Vite 的 `VITE_API_BASE_URL` 读取 HTTP 地址，并自动推导 WebSocket 地址。地址包含后端 `/api` 前缀，不包含接口路径；不在这些文件中填写模型密钥。

- 开发运行：默认读取 `.env.development`，连接 `http://localhost:9000/api`。
- 真机调试：新建 `.env.development.local`，设置为手机可访问的开发服务器地址。手机上的 `localhost` 不指向电脑。
- 生产发行：将 `.env.production.example` 复制为 `.env.production.local`，填写实际 HTTPS 后端地址，并在微信小程序后台配置合法域名。
- `.env.local` 和 `.env.*.local` 已忽略提交。修改环境配置后需要重新编译。
- 未配置后端地址时，请求层直接返回可读错误，不会向当前页面地址误发 API 请求。

示例（请替换为实际域名）：

```dotenv
VITE_API_BASE_URL=https://api.example.com/api
```

## 现阶段接口限制

商品、资料、讲解和语音任务已持久化。文本和语音 API 接入步骤见项目根目录 `PROVIDER_SETUP.md`。未配置时返回明确错误，不再返回假成功或无对应任务的 `PENDING`。
