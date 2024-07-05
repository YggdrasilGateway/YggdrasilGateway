# CONTRIBUTING

欢迎来到这里!

YggdrasilGateway 是一个 Web 程序，需要一个 MySQL 服务器提供数据存储

## 前端资源准备

如上文所描述, YggdrasilGateway 是一个 Web 项目, 所以在下载此项目（后端）的同时,
还需要下载前端页面一同进行修改.

前端地址: https://github.com/YggdrasilGateway/GatewayFrontend

## 本地环境准备

YggdrasilGateway 程序主入口点位于 `components/gateway-bootstrap/src/Main.kt`,
此入口点会自动引用 `components` 下的所有模块, 所以, 当添加一个新的 component 的时候,
您只需要刷新 IDE 的 Gradle 解析即可自动将新的 component 添加到程序入口点中.

我们建议在后端项目根目录创建名为 `local` 的文件夹, 并将入口点的运行目录修改为此目录.

您需要修改 `config/frontend.properties`, 将 `dev.url` 修改为 `http://127.0.0.1:35774`,
然后即可通过后端 http entrypoint 进行调试
