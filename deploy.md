Set-Location 'F:\01workspace\myprojects\super\.java'
Get-NetTCPConnection -LocalPort 9000 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
mvn package
java '-Dfile.encoding=UTF-8' -jar target/type-stars-0.0.1-SNAPSHOT.jar '--spring.profiles.active=dev,local' '--spring.config.additional-location=file:F:/01workspace/myprojects/super/.local/' '--server.address=127.0.0.1'


$env:UNI_INPUT_DIR = 'F:\01workspace\myprojects\super\.uni-app'
$env:UNI_OUTPUT_DIR = 'F:\01workspace\myprojects\super\.local\h5'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:9000/api'
Set-Location 'D:\install_path\HBuilderX\plugins\uniapp-cli-vite'
& 'D:\install_path\HBuilderX\plugins\node\node.exe' 'D:\install_path\HBuilderX\plugins\uniapp-cli-vite\node_modules\@dcloudio\vite-plugin-uni\bin\uni.js' -p h5 --host 127.0.0.1 --port 5173