

# Monitor 接口说明

## 基础信息

- **基础 URL**: `http://localhost:8080`
- **数据格式**: JSON
- **编码**: UTF-8

## 接口列表

### 1. 健康检查

**接口**: `GET /health`

**描述**: 检查服务器是否正常运行

**响应示例**:
```json
{
  "status": "ok"
}
```

---

### 2. 获取设备信息

**接口**: `GET /device-info`

**描述**: 获取设备的详细信息，包括硬件、系统、CPU、内存、存储、电池等

**响应示例**:
```json
{
  "device": {
    "manufacturer": "Xiaomi",
    "brand": "Redmi",
    "model": "M2012K11AC",
    "device": "venus",
    "product": "venus",
    "hardware": "qcom",
    "board": "venus",
    "serial": "xxxxx"
  },
  "android": {
    "version": "13",
    "sdkInt": 33,
    "codename": "REL",
    "incremental": "V14.0.6.0.TKBCNXM",
    "securityPatch": "2023-11-01"
  },
  "build": {
    "id": "TKQ1.221114.001",
    "display": "TKQ1.221114.001 stable",
    "fingerprint": "Redmi/venus/venus:13/TKQ1.221114.001/V14.0.6.0.TKBCNXM:user/release-keys",
    "tags": "release-keys",
    "type": "user",
    "time": 1699526400000
  },
  "display": {
    "width": 1080,
    "height": 2400,
    "rotation": 0,
    "layerStack": 0,
    "flags": 0
  },
  "cpu": {
    "abi": "arm64-v8a",
    "abi2": "",
    "supportedAbis": ["arm64-v8a", "armeabi-v7a", "armeabi"],
    "cores": 8,
    "model": "Qualcomm Technologies, Inc SM8350",
    "frequencies": {
      "min": "300 MHz",
      "max": "2840 MHz",
      "current": "1804 MHz"
    }
  },
  "memory": {
    "total": "11.45 GB",
    "totalBytes": 12297420800,
    "available": "5.23 GB",
    "availableBytes": 5614592000,
    "used": "6.22 GB",
    "usedBytes": 6682828800,
    "usagePercent": 54.35
  },
  "storage": {
    "internal": {
      "total": "234.56 GB",
      "totalBytes": 251968217088,
      "available": "123.45 GB",
      "availableBytes": 132587520000,
      "used": "111.11 GB",
      "usedBytes": 119380697088,
      "usagePercent": 47.38
    },
    "external": {
      "state": "mounted",
      "total": "234.56 GB",
      "totalBytes": 251968217088,
      "available": "123.45 GB",
      "availableBytes": 132587520000,
      "used": "111.11 GB",
      "usedBytes": 119380697088,
      "usagePercent": 47.38
    }
  },
  "battery": {
    "level": 85,
    "status": "Charging",
    "health": "Good",
    "temperature": 32.5,
    "temperatureUnit": "°C",
    "voltage": 4.35,
    "voltageUnit": "V"
  }
}
```

---

### 3. 截屏

**接口**: `GET /screenshot`

**描述**: 获取设备屏幕截图

**查询参数**:
- `width` (可选): 截图宽度，默认为屏幕原始宽度
- `height` (可选): 截图高度，默认为屏幕原始高度

**示例**:
```
GET /screenshot
GET /screenshot?width=720&height=1280
```

**响应**: 返回 PNG 格式的图片数据
- Content-Type: `image/png`

---

### 4. 获取应用列表

**接口**: `GET /packages`

**描述**: 获取已安装的应用列表，支持分页和过滤

**查询参数**:
- `page` (可选): 页码，从 0 开始，默认 0
- `pageSize` (可选): 每页数量，默认 20
- `system` (可选): 是否包含系统应用，`true` 或 `false`，默认 `true`
- `user` (可选): 是否包含用户应用，`true` 或 `false`，默认 `true`
- `name` (可选): 按应用名称搜索（模糊匹配）

**示例**:
```
GET /packages
GET /packages?page=0&pageSize=50
GET /packages?system=false&user=true
GET /packages?name=WeChat
```

**响应示例**:
```json
{
  "packages": [
    {
      "packageName": "com.android.settings",
      "name": "Settings",
      "versionName": "13",
      "versionCode": 33,
      "isSystemApp": true,
      "enabled": true,
      "installTime": 1699526400000,
      "updateTime": 1699526400000
    },
    {
      "packageName": "com.tencent.mm",
      "name": "WeChat",
      "versionName": "8.0.42",
      "versionCode": 2320,
      "isSystemApp": false,
      "enabled": true,
      "installTime": 1699612800000,
      "updateTime": 1699699200000
    }
  ],
  "total": 245,
  "page": 0,
  "pageSize": 20,
  "totalPages": 13
}
```

---

### 5. 获取应用图标

**接口**: `GET /{packageName}/icon`

**描述**: 获取指定应用的图标

**路径参数**:
- `packageName`: 应用包名

**示例**:
```
GET /com.android.settings/icon
GET /com.tencent.mm/icon
```

**响应**: 返回 PNG 格式的图标数据
- Content-Type: `image/png`

---

### 6. APK 文件上传和安装

**接口**: `POST /upload`

**描述**: 上传 APK 文件并安装，支持断点续传

#### 6.1 单文件上传

**请求体**:
```json
{
  "filename": "app.apk",
  "data": "base64_encoded_apk_data"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "APK uploaded and installed successfully"
}
```

#### 6.2 分片上传（支持断点续传）

**请求体**:
```json
{
  "filename": "app.apk",
  "data": "base64_encoded_chunk_data",
  "chunkIndex": 0,
  "totalChunks": 10
}
```

**参数说明**:
- `filename`: 文件名
- `data`: 当前分片的 base64 编码数据
- `chunkIndex`: 当前分片索引（从 0 开始）
- `totalChunks`: 总分片数

**响应示例** (未完成):
```json
{
  "success": true,
  "message": "Chunk received",
  "complete": false,
  "progress": {
    "filename": "app.apk",
    "totalChunks": 10,
    "receivedChunks": 7,
    "isComplete": false,
    "progress": 70.00
  }
}
```

**响应示例** (完成):
```json
{
  "success": true,
  "message": "Upload complete",
  "complete": true,
  "path": "/data/local/tmp/uploads/app.apk",
  "size": 52428800
}
```

#### 6.3 查询上传进度

**接口**: `GET /upload?filename={filename}`

**示例**:
```
GET /upload?filename=app.apk
```

**响应示例**:
```json
{
  "filename": "app.apk",
  "totalChunks": 10,
  "receivedChunks": 7,
  "isComplete": false,
  "progress": 70.00
}
```

#### 6.4 手动安装

**请求体**:
```json
{
  "filename": "app.apk",
  "action": "install"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "APK installed successfully"
}
```

---

## 错误响应

所有接口在出错时返回如下格式：

```json
{
  "error": "错误描述信息"
}
```

**常见错误**:
- `404 Not Found`: 请求的资源不存在
- `400 Bad Request`: 请求参数错误
- `405 Method Not Allowed`: HTTP 方法不支持
- `500 Internal Server Error`: 服务器内部错误

---

## 使用示例

### 使用 curl 测试

```bash
# 健康检查
curl http://localhost:8080/health

# 获取设备信息
curl http://localhost:8080/device-info

# 截屏并保存
curl http://localhost:8080/screenshot > screenshot.png
curl "http://localhost:8080/screenshot?width=720&height=1280" > screenshot_small.png

# 获取应用列表
curl http://localhost:8080/packages
curl "http://localhost:8080/packages?page=0&pageSize=50&system=false"

# 获取应用图标
curl http://localhost:8080/com.android.settings/icon > icon.png

# 上传并安装 APK（单文件）
curl -X POST http://localhost:8080/upload \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "app.apk",
    "data": "'$(base64 -i app.apk)'"
  }'

# 分片上传
# 分片 0
curl -X POST http://localhost:8080/upload \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "app.apk",
    "data": "'$(base64 -i app.apk.part0)'"
    "chunkIndex": 0,
    "totalChunks": 3
  }'

# 查询上传进度
curl "http://localhost:8080/upload?filename=app.apk"

# 手动安装
curl -X POST http://localhost:8080/upload \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "app.apk",
    "action": "install"
  }'
```

---

## 注意事项

1. **端口转发**: 使用前需先执行 `adb forward tcp:8080 localabstract:scrcpy_monitor`
2. **权限要求**: 某些操作需要 root 权限或系统权限
3. **文件大小**: 分片上传建议每片 1-2MB，避免单次传输过大
4. **超时设置**: 大文件上传建议设置较长的超时时间
5. **并发限制**: 避免同时上传多个文件
6. **临时文件**: 上传的文件保存在 `/data/local/tmp/uploads/` 目录
7. **断点续传**: 分片可以乱序上传，重复上传会自动跳过


## server运行方法
```bash
# 清除现有的端口转发
adb forward --remove tcp:8080

# 或清除所有
adb forward --remove-all

# 然后重新转发
adb forward tcp:8080 localabstract:scrcpy_monitor

adb shell CLASSPATH=/data/local/tmp/scrcpy-server.jar \
    app_process / com.genymobile.scrcpy.Server 3.3.3 monitor=true
    
```
