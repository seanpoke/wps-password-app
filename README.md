# WPS Password Manager

WPS密码管理器是一款Android应用，用于管理和保护WPS文档的密码信息。该应用通过监听文件系统变化，自动将密码元数据写入Office文档（.docx、.xlsx、.pptx），实现密码与文档的绑定存储。

## 功能特性

- **文件监控**: 自动监控 `WpsManagement` 目录下的Office文档变化
- **密码绑定**: 通过ZIP Extra Field方式将密码写入文档尾部
- **WPS集成**: 支持扫描和关联已安装的WPS应用
- **网络服务**: 支持登录认证、心跳保活、密钥获取等功能
- **目录迁移**: 支持文档目录迁移功能

## 技术架构

### 项目结构

```
app/src/main/java/com/wpspasswordmanager/
├── WpsPasswordManagerApplication.kt  # 应用入口
├── business/                          # 业务逻辑层
│   ├── FileMeta.kt                   # 文件元数据模型
│   ├── FileMetaFactory.kt            # 元数据工厂
│   ├── FileMetaManager.kt            # 元数据管理器
│   ├── OfficeEncryptUtils.kt         # Office加密工具
│   ├── PasswordGenerator.kt          # 密码生成器
│   ├── WpsAppInfo.kt                 # WPS应用信息
│   ├── WpsManager.kt                 # WPS应用管理
│   └── ZipExtraFieldManager.kt       # ZIP扩展字段管理
├── monitor/                          # 监控服务
│   ├── AccessibilityServiceManager.kt
│   ├── MetadataWriteService.kt
│   └── WpsAccessibilityService.kt
├── network/                          # 网络模块
│   ├── ApiResponse.kt
│   ├── HeartbeatService.kt           # 心跳服务
│   └── NetworkManager.kt             # 网络管理
├── storage/                          # 存储模块
│   ├── ConfigStorage.kt              # 配置存储
│   └── ServerConfig.kt               # 服务器配置
├── ui/                               # UI层
│   ├── MainActivity.kt               # 主界面
│   ├── LogActivity.kt                # 日志界面
│   ├── NotificationManager.kt
│   ├── FloatingButtonService.kt      # 悬浮按钮
│   └── adapters/
└── utils/                            # 工具类
    └── LogManager.kt                 # 日志管理
```

### 核心技术

1. **文件监控**: 使用 `FileObserver` 监听目录变化，支持防抖处理
2. **密码存储**: 通过ZIP Extra Field方式将密码元数据写入Office文档尾部
3. **无障碍服务**: 监听WPS应用操作，实现自动密码填充
4. **网络通信**: 使用OkHttp进行HTTP请求，支持会话保持和自动重连

## 快速开始

### 环境要求

- Android SDK 26+ (Android 8.0)
- Gradle 8.1.2
- Kotlin 1.9.0

### 构建与运行

```bash
# 克隆项目
git clone <repository-url>
cd wps-password-app

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

1. **无障碍服务**: 用于监听WPS应用操作
2. **悬浮窗权限**: 用于显示悬浮按钮
3. **文件管理权限**: 用于访问和修改文档文件

### 配置步骤

1. **启动应用**: 打开WPS密码管理器
2. **授予权限**: 依次启用无障碍服务、悬浮窗权限和文件管理权限
3. **配置服务器**: 输入服务器IP、端口、用户名和密码
4. **选择WPS应用**: 扫描并选择默认的WPS应用

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