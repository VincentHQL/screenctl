# 群控客户端

# 设备添加流程
1. 支持直连添加和无线调试添加设备
   1. 直连添加：输入手机端ip和adb 端口。（网络adb连接）
   2. 无线调试：输入手机ip和adb端口，还需填写配对端口和配对码。
2. 连接设备成功后，需推送scrcpy-server.jar到手机端并启动。
3. 将scrcpy-server.jar建立的http server服务映射到客户端。后续通过http client uninx socket访问。
4. 到控制界面时，调用startStream启动另一个进程并