# NJOCR - 安卓本地离线文字识别 (NCNN + PaddleOCRv5)

> 🚀 **专为安卓自动化脚本打造的本地 OCR 服务端，毫秒级响应，完全离线！**

## 📺 视频演示

[![Bilibili 演示视频](https://img.shields.io/badge/Bilibili-观看实测演示(BV1bWfdBnEuK)-fb7299?style=for-the-badge&logo=bilibili)](https://www.bilibili.com/video/BV1bWfdBnEuK)

> 👆 **点击上方按钮观看实测：OPPO R15 老机型开启 GPU 加速后，识别速度翻倍（12s → 5s）！**

## 🔥 核心优势

- **💯 完全离线**：纯本地运行，**无需联网**，无需上传图片到服务器，保护隐私且速度极快。
- **⚡ 极致性能**：
  - 基于 **NCNN** 推理框架，支持 **Vulkan GPU** 硬件加速。
  - 集成 **PaddleOCR v5** 轻量级模型，针对移动端深度调优。
  - 实测骁龙 660 等老旧机型也能流畅运行。
- **🤝 广泛兼容**：
  - 采用标准 **HTTP POST** 接口 (Port 1666)。
  - **完美支持所有自动化工具**：
    - ✅ **Auto.js / AutoX**
    - ✅ **按键精灵手机版 (Mobile Anjian)**
    - ✅ **懒人精灵 (LazyOffice)**
    - ✅ **EasyClick (EC)**
    - ✅ **AutoGo**
    - ...以及任何支持 HTTP 请求的编程语言。

## 🛠️ 快速开始

### 1. 下载安装
请前往右侧 [Releases](https://github.com/nanjiang1986/NJOCR-NCNN/releases) 页面下载最新 APK 并安装到手机。
*注意：首次运行请务必授予“管理所有文件”权限，以便读取截图。*

### 2. 接口调用说明
- **请求地址**: `http://127.0.0.1:1666`
- **请求方式**: `POST`
- **参数**:
  - `path`: 图片绝对路径 (例如 `/sdcard/1.png`)
  - `type`: 返回数据格式 (1=纯文本, 2=JSON坐标)

### 3. 代码示例

#### Auto.js / AutoX
```javascript
var url = "[http://127.0.0.1:1666](http://127.0.0.1:1666)";
var res = http.post(url, {
    "path": "/sdcard/test.png",
    "type": "1"
});
toastLog("识别结果: " + res.body.string());
