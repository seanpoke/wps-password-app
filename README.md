# 文档编辑器密码管理器

文档编辑器密码管理器是一款Android应用，用于管理和保护Office文档的密码信息。该应用通过监听文件系统变化，自动将密码元数据写入Office文档（.docx、.xlsx、.pptx），实现密码与文档的绑定存储。

## 功能特性

- **文件监控**: 自动监控 `DocumentManagement` 目录下的Office文档变化
- **密码绑定**: 通过ZIP Extra Field方式将密码写入文档尾部
- **文档编辑器集成**: 支持扫描和关联已安装的文档编辑器应用
- **网络服务**: 支持登录认证、心跳保活、密钥获取等功能
- **目录迁移**: 支持文档目录迁移功能

## 技术架构

### 项目结构

```
app/src/main/java/com/documentpasswordmanager/
├── DocumentPasswordManagerApplication.kt  # 应用入口
├── business/                          # 业务逻辑层
│   ├── FileMeta.kt                   # 文件元数据模型
│   ├── FileMetaFactory.kt            # 元数据工厂
│   ├── FileMetaManager.kt            # 元数据管理器
│   ├── OfficeEncryptUtils.kt         # Office加密工具
│   ├── PasswordGenerator.kt          # 密码生成器
│   ├── DocumentAppInfo.kt            # 文档编辑器应用信息
│   ├── DocumentManager.kt            # 文档编辑器应用管理
│   └── ZipExtraFieldManager.kt       # ZIP扩展字段管理
├── monitor/                          # 无障碍服务、悬浮窗管理、元数据写入
├── network/                          # OkHttp 封装、心跳保活
├── storage/                          # 本地配置与用户信息
├── ui/                               # ProxyActivity（文档打开代理）、FloatingButtonService（悬浮按钮）
│   └── compose/                      # Compose 入口 AppActivity、AppViewModel、组件、页面、悬浮层
│       ├── assistant/                # 悬浮窗上的 Compose 权限树
│       └── screens/                  # 各 Compose 页面
└── utils/                            # 日志等工具
```

> UI 已全面迁移到 Compose，入口为 `ui/compose/AppActivity`；旧的 XML Activity（MainActivity / LogActivity 等）已删除。
> 客户端接口契约以 `.doc/客户端接口文档.md` 为准，该文档已纳入版本控制。

### 核心技术

1. **文件监控**: 使用 `FileObserver` 监听目录变化，支持防抖处理
2. **密码存储**: 通过ZIP Extra Field方式将密码元数据写入Office文档尾部
3. **无障碍服务**: 监听文档编辑器应用操作，实现自动密码填充
4. **网络通信**: 使用OkHttp进行HTTP请求，支持会话保持和自动重连

## 快速开始

### 环境要求

- Android SDK 26+ (Android 8.0)，compileSdk 34
- Gradle 8.2
- Kotlin 1.9.0
- **JDK 17**（必须）

> 构建注意：若 `JAVA_HOME` 指向 JDK 21，`jlink` 处理 `android-34/core-for-system-modules.jar` 会报
> `ModuleTarget is malformed: platformString missing delimiter: android` 导致 `compileDebugJavaWithJavac` 失败。
> 本机 JDK 17 位于 `E:\JDK\jdk-17.0.19`，请显式指定（不要写进 `gradle.properties` 提交绝对路径）：

```powershell
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=E:\JDK\jdk-17.0.19" assembleDebug
```

### 构建与运行

```bash
# 克隆项目
git clone <repository-url>
cd document-password-app

# 构建Debug版本
./gradlew assembleDebug

# 构建Release版本
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

### GitHub Actions自动构建

项目已配置GitHub Actions自动构建，每次推送代码到`main`或`master`分支时自动构建Debug版本APK。

构建产物可在Actions页面的Artifacts中下载。

## 使用说明

### 权限要求

应用需要以下权限：

1. **无障碍服务**: 用于监听文档编辑器应用操作
2. **悬浮窗权限**: 用于显示悬浮按钮
3. **文件管理权限**: 用于访问和修改文档文件

### 配置步骤

1. **启动应用**: 打开文档编辑器密码管理器
2. **授予权限**: 依次启用无障碍服务、悬浮窗权限和文件管理权限
3. **配置服务器**: 输入服务器IP、端口、用户名和密码
4. **选择文档编辑器应用**: 扫描并选择默认的文档编辑器应用

### 密码管理流程

1. 创建或编辑Office文档（.docx、.xlsx、.pptx）
2. 保存文档到 `Documents/WpsManagement` 目录
3. 应用自动监听文件变化并写入密码元数据
4. 下次打开文档时自动读取密码

## API接口

### 登录接口

```
POST /api/login
Content-Type: application/json

{
    "username": "string",
    "password": "string"
}
```

### 心跳接口

```
POST /api/heartbeat
Headers: Authorization: Bearer <token>
```

### 获取密钥接口

```
GET /api/key/latest
Headers: Authorization: Bearer <token>
```

## 开发说明

### 代码规范

- 使用Kotlin语言编写
- 遵循Android开发最佳实践
- 使用MVVM架构模式
- 代码注释清晰，便于维护

### 测试

```bash
# 运行单元测试
./gradlew test

# 运行Android测试
./gradlew connectedAndroidTest
```

## 许可证

MIT License

## 贡献

欢迎提交Issue和Pull Request！