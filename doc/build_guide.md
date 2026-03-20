# WPS密码管理辅助应用 - 编译指南

## 环境配置步骤

1. **安装Android Studio**
   - 下载并安装Android Studio 2023.1.1 Patch 2
   - 安装过程中确保选择安装Android SDK

2. **配置JDK**
   - 确保安装了JDK 17版本
   - 在系统环境变量中设置JAVA_HOME指向JDK安装目录

3. **配置Android SDK**
   - 打开Android Studio，进入SDK Manager
   - 安装以下SDK组件：
     - Android SDK Build-Tools 34.0.0
     - Android SDK Platform 34
     - Android SDK Platform-Tools
     - Android SDK Tools (Obsolete)

4. **配置环境变量**
   - 设置ANDROID_HOME指向Android SDK安装目录
   - 将`%ANDROID_HOME%\platform-tools`和`%ANDROID_HOME%\tools`添加到PATH环境变量

## 项目导入流程

1. **克隆项目**
   - 从版本控制系统克隆项目到本地

2. **打开项目**
   - 启动Android Studio
   - 选择"Open an existing project"
   - 导航到项目目录并选择打开

3. **同步Gradle**
   - Android Studio会自动检测并同步Gradle配置
   - 如果提示需要下载Gradle，点击"OK"确认

4. **检查依赖项**
   - 确保所有依赖项都已正确下载
   - 如果有依赖项下载失败，尝试点击"Sync Project with Gradle Files"按钮

## 依赖项检查与配置

1. **检查build.gradle文件**
   - 确保`build.gradle`文件中的依赖项版本正确
   - 确保`compileSdkVersion`、`minSdkVersion`和`targetSdkVersion`设置正确

2. **检查local.properties文件**
   - 确保`local.properties`文件中包含正确的Android SDK路径
   - 例如：`sdk.dir=C:\Users\YourUsername\AppData\Local\Android\Sdk`

3. **检查Gradle版本**
   - 确保使用Gradle 8.2版本
   - 检查`gradle-wrapper.properties`文件中的`distributionUrl`设置

## APK生成步骤

1. **构建项目**
   - 点击"Build" -> "Make Project"编译项目
   - 确保编译过程无错误

2. **生成Debug APK**
   - 点击"Build" -> "Build Bundle(s) / APK(s)" -> "Build APK(s)"
   - 等待构建完成，Android Studio会显示APK生成位置

3. **生成Release APK**
   - 点击"Build" -> "Generate Signed Bundle / APK"
   - 选择"APK"并点击"Next"
   - 创建或选择一个密钥库文件
   - 填写密钥库信息并点击"Next"
   - 选择"Release"构建类型并点击"Finish"
   - 等待构建完成，Android Studio会显示APK生成位置

## 签名配置

1. **创建密钥库**
   - 在生成Release APK时，点击"Create new..."创建新的密钥库
   - 填写密钥库路径、密码、别名和密码
   - 点击"OK"保存密钥库信息

2. **配置签名信息**
   - 在`app/build.gradle`文件中添加签名配置
   - 例如：
   ```groovy
   android {
       ...
       signingConfigs {
           release {
               storeFile file('your-keystore.jks')
               storePassword 'your-keystore-password'
               keyAlias 'your-key-alias'
               keyPassword 'your-key-password'
           }
       }
       buildTypes {
           release {
               signingConfig signingConfigs.release
               ...
           }
       }
   }
   ```

3. **使用密钥库**
   - 确保密钥库文件安全存储
   - 不要将密钥库文件和密码提交到版本控制系统

## 常见问题解决

1. **Gradle同步失败**
   - 检查网络连接
   - 检查Android SDK路径是否正确
   - 尝试清理Gradle缓存：删除`~/.gradle/caches`目录

2. **构建失败**
   - 检查代码中是否有语法错误
   - 检查依赖项是否冲突
   - 尝试执行"Clean Project"后重新构建

3. **APK安装失败**
   - 检查设备是否启用了"未知来源"安装权限
   - 检查APK签名是否正确
   - 检查设备Android版本是否符合最低要求

4. **无障碍服务无法启用**
   - 确保应用已安装并运行
   - 进入系统设置 -> 无障碍 -> 找到WPS密码管理服务并启用
   - 确保服务权限已正确配置

## 性能优化

1. **代码优化**
   - 避免在主线程中执行耗时操作
   - 使用适当的数据结构和算法
   - 减少不必要的对象创建

2. **资源优化**
   - 优化图片资源大小
   - 使用适当的资源配置（如不同屏幕密度的资源）
   - 减少APK大小

3. **电池优化**
   - 避免后台持续运行
   - 使用JobScheduler或WorkManager处理后台任务
   - 优化网络请求频率

## 调试技巧

1. **使用Logcat**
   - 在代码中添加日志输出
   - 使用不同级别的日志（Debug, Info, Warning, Error）
   - 过滤日志以查看特定标签的输出

2. **使用断点**
   - 在关键代码处设置断点
   - 使用Android Studio的调试模式运行应用
   - 检查变量值和执行流程

3. **使用Profiler**
   - 监控应用的CPU、内存和网络使用情况
   - 识别性能瓶颈
   - 优化资源使用

## 版本控制

1. **Git配置**
   - 初始化Git仓库
   - 添加`.gitignore`文件，排除不必要的文件
   - 定期提交代码更改

2. **分支管理**
   - 使用主分支（main）存储稳定版本
   - 使用特性分支开发新功能
   - 使用发布分支准备发布版本

3. **标签管理**
   - 为每个发布版本创建标签
   - 使用语义化版本号（如v1.0.0）

## 总结

本指南提供了WPS密码管理辅助应用的完整编译流程，包括环境配置、项目导入、依赖项检查、APK生成和签名配置等步骤。遵循本指南可以确保应用能够正确构建和运行。

如果在编译过程中遇到问题，请参考常见问题解决部分，或查阅Android Studio官方文档。