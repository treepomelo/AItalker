# 本地运行

## 访问地址

- 商品讲解页面：<http://127.0.0.1:5173/>
- 模型配置页面：<http://127.0.0.1:5173/#/pages/settings/models>，保存后立即生效。
- 后端商品列表：<http://127.0.0.1:9000/api/products>
- MySQL：`127.0.0.1:3306/ts_stars`，账号 `root`，密码位于 `.local/application-local.yml`。
- Redis：`127.0.0.1:6379`，本项目 Redis 数据目录为 `.local/redis`。

后端、前端和本项目启动的 Redis 均只监听 `127.0.0.1`。本地配置和 Redis 数据已通过根目录 `.gitignore` 排除提交。

## 后端重启

先确认本机 MySQL 和 Redis 已启动。使用 `.java` 作为工作目录，保持配置及音频相对路径一致：

```powershell
Set-Location 'F:\01workspace\myprojects\super\.java'
mvn package
java '-Dfile.encoding=UTF-8' -jar target/type-stars-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev,local --spring.config.additional-location=file:F:/01workspace/myprojects/super/.local/ --server.address=127.0.0.1
```

该进程占用终端，可通过 Ctrl+C 停止。必须保留 `.local/application-local.yml`，否则不会加载本次配置的数据库凭据。

## 前端重启

前端使用本机 HBuilderX 的 Vue 3 / Vite 工具链。另开 PowerShell 终端运行：

```powershell
$env:UNI_INPUT_DIR = 'F:\01workspace\myprojects\super\.uni-app'
$env:UNI_OUTPUT_DIR = 'F:\01workspace\myprojects\super\.local\h5'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:9000/api'
Set-Location 'D:\install_path\HBuilderX\plugins\uniapp-cli-vite'
& 'D:\install_path\HBuilderX\plugins\node\node.exe' 'D:\install_path\HBuilderX\plugins\uniapp-cli-vite\node_modules\@dcloudio\vite-plugin-uni\bin\uni.js' -p h5 --host 127.0.0.1 --port 5173
```

Ctrl+C 停止前端服务。若启动时提示端口占用，先检查已经运行的进程，不要重复启动。

## Redis 重启

若 `6379` 没有已有服务，可另开 PowerShell 终端运行：

```powershell
& 'D:\install_path\Redis-x64-5.0.14.1\redis-server.exe' --bind 127.0.0.1 --port 6379 --dir 'F:/01workspace/myprojects/super/.local/redis'
```

本机现有 Redis 是 5.0.14.1，本次仅用于本地运行；项目原部署说明建议 Redis 7。

## 数据库与功能现状

已创建 `ts_stars` 数据库，并使用现有 SQL 建立 `ts_user`、`knowledge_base`、`knowledge_document`、`product`、`product_knowledge_rel`、`product_explanation`、`speech_task` 七张表。初始化使用 `CREATE IF NOT EXISTS`，没有清空已有库表。

商品、知识库、讲解版本和语音任务现已通过 JDBC 持久化到 MySQL；新增商品详情、讲解版本元信息和语音缓存索引三张表。本地启动会幂等加载三款虚构文创商品、九份资料和三份人工示例讲解。文本模型与语音 API 已可配置，详见 `PROVIDER_SETUP.md`。未填凭据时明确返回配置缺失；语音文件保存在 `.local/audio`。
