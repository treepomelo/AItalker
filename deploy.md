Set-Location 'F:\01workspace\myprojects\super\.java'
Get-NetTCPConnection -LocalPort 9000 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
mvn package
java '-Dfile.encoding=UTF-8' -jar target/type-stars-0.0.1-SNAPSHOT.jar '--spring.profiles.active=dev,local' '--spring.config.additional-location=file:F:/01workspace/myprojects/super/.local/' '--server.address=127.0.0.1'


$env:UNI_INPUT_DIR = 'F:\01workspace\myprojects\super\.uni-app'
$env:UNI_OUTPUT_DIR = 'F:\01workspace\myprojects\super\.local\h5'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:9000/api'
Set-Location 'D:\install_path\HBuilderX\plugins\uniapp-cli-vite'
& 'D:\install_path\HBuilderX\plugins\node\node.exe' 'D:\install_path\HBuilderX\plugins\uniapp-cli-vite\node_modules\@dcloudio\vite-plugin-uni\bin\uni.js' -p h5 --host 127.0.0.1 --port 5173


## 停止运行

```powershell
# 停止后端（9000）和前端（5173）
$ports = 9000, 5173
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
	Where-Object { $ports -contains $_.LocalPort } |
	Select-Object -ExpandProperty OwningProcess -Unique |
	ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
```

## 重新运行

后端和前端分别在两个 PowerShell 终端中运行。

### 后端

```powershell
Set-Location 'F:\01workspace\myprojects\super\.java'
mvn package
java '-Dfile.encoding=UTF-8' -jar target/type-stars-0.0.1-SNAPSHOT.jar '--spring.profiles.active=dev,local' '--spring.config.additional-location=file:F:/01workspace/myprojects/super/.local/' '--server.address=127.0.0.1'
```

### 前端

```powershell
$env:UNI_INPUT_DIR = 'F:\01workspace\myprojects\super\.uni-app'
$env:UNI_OUTPUT_DIR = 'F:\01workspace\myprojects\super\.local\h5'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:9000/api'
Set-Location 'D:\install_path\HBuilderX\plugins\uniapp-cli-vite'
& 'D:\install_path\HBuilderX\plugins\node\node.exe' 'D:\install_path\HBuilderX\plugins\uniapp-cli-vite\node_modules\@dcloudio\vite-plugin-uni\bin\uni.js' -p h5 --host 127.0.0.1 --port 5173
```