# 模型、语音与测试商品

## 填入 API 后启用

打开底部「模型配置」或访问 <http://127.0.0.1:5173/#/pages/settings/models>。聊天与语音独立选择厂商模板、接口协议，填写服务地址、Key、模型 ID；语音另填音色。点击「测试普通 + 流式」或「测试语音接口」，检查结果后「保存并应用」，无需重启。

测试使用当前表单草稿，会产生少量真实请求和可能的厂商费用；只有保存才切换业务配置。已配置不代表验证通过，修改参数或 Key 后需重新测试。聊天普通回复和流式回复分别检测，语音检查 MP3 响应。测试与商品讲解、聊天、语音生成共用适配器。

保存文件为 `.local/model-settings.json`（默认以 `.java` 为工作目录），包含服务端密钥，已排除 Git 提交，不要分享该文件。浏览器不保存、不回显密钥；留空保留，显式清除后停用。切换服务主机需要重新填写 Key。文件原子写入；版本冲突会拒绝覆盖，点「重新读取」后再编辑。测试状态按配置指纹隔离，仅保存在内存，后端重启后需重新测试。

启动优先级：已保存的 JSON 配置 > Spring 环境变量 / 本地 YAML 初始配置。JSON 保存两个通道的完整快照；存在时，修改环境变量不会覆盖它。可用 `--super.config.path=绝对路径` 指定配置文件。初次未保存时仍可编辑 `.local/application-local.yml` 的 `super.ai` / `super.speech`，模板为 `.java/provider-config.example.yml`，或使用以下环境变量；这种方式需要重启，初始协议为 OpenAI 兼容。

| 参数 | 文本模型 | 语音模型 |
| --- | --- | --- |
| 服务根地址 | `SUPER_AI_BASE_URL` | `SUPER_SPEECH_BASE_URL` |
| API Key | `SUPER_AI_API_KEY` | `SUPER_SPEECH_API_KEY` |
| 供应商支持的模型 ID | `SUPER_AI_MODEL` | `SUPER_SPEECH_MODEL` |
| 音色 ID | 不适用 | `SUPER_SPEECH_VOICE`，默认 `alloy` |

地址应包含供应商要求的版本前缀，例如 `https://api.openai.com/v1`。支持根地址或所选协议对应的完整接口地址，避免重复拼接；页面显示已保存配置实际使用的地址。模型和音色填写账号实际支持的 ID，模板不自动猜测或查询模型列表。

模型身份和回答风格通过独立的 `.local/prompts.yml` 文件控制，模板见 `.java/prompt-config.example.yml`。其中 `prompts.assistant` 控制普通聊天，`prompts.explanation` 控制商品讲解，`prompts.question` 控制商品知识问答。默认路径可通过 `--super.prompt-config.path=绝对路径` 覆盖。商品知识资料仍由服务端追加，提示词不能跨商品读取资料。

讲解提示词在每次生成时重新读取，修改后无需重启；普通聊天和商品问答提示词仍需重启。讲解输入会过滤价格、交易和售后句子，生成结果还会经过关键词及金额格式校验（包括中文金额）；命中时返回 `EXPLANATION_CONTENT_REJECTED`，不保存该结果。该校验不能替代人工事实核对。历史讲解不会自动改写，含上述内容的旧版本会拒绝再次合成语音，请先生成新版本。

## 协议兼容范围

| 协议 | 请求与响应 | 可选厂商 |
| --- | --- | --- |
| `OPENAI_CHAT` | Bearer，`/chat/completions`，普通 JSON / SSE | OpenAI、DeepSeek、通义兼容模式、硅基流动、豆包 Ark 等提供兼容端点的服务 |
| `ANTHROPIC` | x-api-key + anthropic-version，`/messages`，独立 system，原生 SSE | Claude Messages |
| `GEMINI` | x-goog-api-key，contents / parts，generateContent / streamGenerateContent | Gemini 原生 API |
| `OPENAI_SPEECH` | Bearer，`/audio/speech`，input / voice / speed，MP3 二进制 | OpenAI、硅基流动等兼容语音端点 |
| `MINIMAX_SPEECH` | Bearer，`/t2a_v2`，voice_setting，业务状态检查与 hex MP3 解码 | MiniMax 中国区 / 国际区 |
| `TENCENT_SPEECH` | TC3-HMAC-SHA256 签名，Key 填 `SecretId:SecretKey`，模型字段填数字音色 ID（如 `601000`），固定 `ap-guangzhou`，仅输出 mp3 | 腾讯云语音合成 TextToVoice |

腾讯云单次合成上限 150 汉字，讲解文本按标点自动分段（每段 100 字内）多次合成并拼接 MP3；语速按 0.25–4 线性映射到 -2–6。需在腾讯云控制台开通语音合成服务并确认所选音色权限；Region 暂不支持在页面修改。

OpenAI 兼容聊天可按模型选择 `max_tokens`、`max_completion_tokens` 或 `omit`，并填写白名单 JSON 扩展参数，例如 `{"enable_thinking":false}`。不允许覆盖 model、messages、stream 或鉴权，防止配置页显示值和实际调用不一致。每次请求及排队语音任务都捕获不可变配置快照，保存不会中途切换正在执行的任务；语音缓存包含配置指纹。

兼容的是指定协议，不保证同厂商的所有模型或 API：Azure 专用鉴权、OpenAI Responses、Claude 需额外 workspace 头的密钥、豆包/讯飞原生语音尚未适配。此处为文字聊天和文字转语音，尚不包含语音识别或实时双向音频。不同账号地区、额度、音色权限和模型参数要求必须用真实凭据测试；本地自动化验证使用模拟厂商服务，不证明线上账号可用。

## 配置访问权限

仅在 `local` profile、请求来自回环地址且 Host 为 localhost / 回环地址时，允许本机无登录管理。浏览器 Origin 仅允许 localhost / 127.0.0.1 的 5173、9000 端口；写入和测试还需 `X-Config-Request: 1`。非本机或非 local 模式要求登录并具有 ADMIN 角色。手机/远程小程序不会获得本地管理豁免。该规则不会开启匿名聊天。

- `GET /api/admin/model-settings`：脱敏配置、实际地址、当前验证状态。
- `PUT /api/admin/model-settings/{chat|speech}`：保存配置，需 revision、config、clearKey。
- `POST /api/admin/model-settings/{chat|speech}/test`：测试同结构草稿，不切换生效配置。

官方协议参考：[OpenAI 语音](https://developers.openai.com/api/docs/guides/text-to-speech)、[Claude Messages](https://platform.claude.com/docs/en/api/messages)、[Gemini GenerateContent](https://ai.google.dev/api/generate-content)、[MiniMax 语音](https://platform.minimax.io/docs/api-reference/speech-t2a-http)、[腾讯云语音合成](https://cloud.tencent.com/document/product/1073/37995)。

`GET /api/capabilities` 只返回功能是否配置和允许显示的模型名，不返回 Key。配置完整不等于连接已验证；无效 Key、超时、限流、非音频响应均通过明确错误反馈。

## 如何试用

1. 打开商品页，选择一款商品，查看规格与三份知识资料。
2. 即使没有文本模型，也可查看标为“人工编写的示例讲解”的内容。
3. 在配置页填好语音地址、Key、模型和音色，保存后返回商品页点击“生成 / 获取语音”。
4. 前端查询任务状态，完成后显示播放、暂停、继续和停止按钮。相同版本和参数复用音频，失败可重试。
5. 填好文本模型后，点击“生成新讲解”；只有模型成功返回正文才保存新版本，失败保留原来的讲解。

语音文件默认保存在本机 `.local/audio`，通过后端音频接口提供；本地试用无需额外配置 OSS。部署到其他机器时需保留音频目录或实现对象存储适配；目前没有自动文件过期清理。

## 接口

- `GET /api/products`：商品列表与详细规格。
- `GET /api/products/{id}/knowledge`：该商品公开测试资料。
- `WebSocket /api/product-chat/{token}/{productId}`：基于当前商品知识资料的流式问答；消息为 `{"productId":1001,"question":"问题"}`。本地 `local` profile 可使用 `local` 标识，其他环境要求登录 Token。
- `POST /api/products/{id}/explanations`：真实模型生成，参数 `scenario`、`tone`、`duration`；缺配置返回 HTTP 503 / `MODEL_NOT_CONFIGURED`。
- `GET /api/products/{id}/explanations`：讲解历史。
- `POST /api/products/{id}/explanations/{explanationId}/audio`：创建或复用真实语音任务；缺配置返回 HTTP 503 / `SPEECH_NOT_CONFIGURED`，不创建假任务。
- `GET /api/audio-tasks/{taskId}`：状态与错误；`COMPLETED` 时返回相对 API 根路径的 `audioUrl`。
- `GET /api/audio-tasks/{taskId}/file`：MP3 音频，支持标准 Resource 响应。

现阶段讲解生成是有超时的同步请求；语音使用有界线程池和持久化任务。语音任务在单实例重启后标记失败供重试，尚不是多实例分布式任务系统。

## 测试数据

`DEMO_PRODUCTS.json` 为便于查看的测试资料；数据库种子为 `.java/src/main/resources/db/demo-data.sql`。

- `1001` 江南花窗 · 黄铜流苏书签，演示价 ¥68。
- `1002` 宋韵月白 · 青瓷品茗杯，演示价 ¥128。
- `1003` 敦煌色谱 · 丝质小方巾，演示价 ¥198。

均为虚构“青禾文创”测试商品，不宣称真实销售、博物馆授权或非遗认证。每款包含独立知识库、三份资料和一份人工示例讲解；不会冒充真实商品信息或模型输出。

本地 profile 开启 SQL 初始化，按固定测试 ID 插入，重复启动不覆盖已有记录。商品、资料、讲解版本、语音任务已改用 MySQL；语音字节保存在本地文件系统。生产环境不应自动加载这些测试种子，应先单独执行 `db/schema.sql` 的增量建表。

## 实时聊天

已恢复独立的实时聊天页，使用服务器配置的文本模型，通过 `/api/text-chat/{token}` 流式回复。本地 `local` profile 下使用固定的 `local` 标识即可测试，不要求登录；其他环境仍要求有效登录 Token。当前会话仅保留在页面内，完整登录体验与聊天历史持久化仍待明确访问规则后完善。

管理知识库接口已改为 MySQL，并在入口校验登录及 ADMIN 角色。本次公开资料接口仅面向公开测试商品，不应用来提供私有知识库全文。
