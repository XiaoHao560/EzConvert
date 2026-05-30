## [changelog] 2026-05-28

### 新增
- 新增 `选择文件` 时支持多选文件 #148
- 新增 `接收分享文件` 时支持接收多个分享文件
- 新增 `主题设置`
- 新增 `动态取色` 功能 (Material You)

### 修复
- 修复 应用启动时权限检查重复触发的问题
- 修复 设置界面打开时误触发保存配置的问题
- 修复 `预览` 视频进度条在播放结束时因浮点精度误差导致的崩溃
- 修复 重建 Activity 导致的 `检查更新` 崩溃
- 修复 `预览/媒体信息` 视频"源帧率"始终显示 45000.00 fps 的问题

### 优化
- 优化 `保存设置` 时添加保存防抖机制

### 变更
- 修改 应用安卓版本为 **Android15**
- 修改 FFmpegKit 库，适配 Android15 的 16kb页大小
- 修改 `日志设置界面` 日志等级设置相关文本
- 修改 `主界面` FFmpeg 与 FFmpegKit 版本显示
- 修改 `主界面` 自定义音量的 slider 为整数增加 (stepSize="1")

### 移除
- 移除 `主界面` 自定义音量的 slider 的点击反馈 (haloColor)

### 代码变更
- 新增 FFmpeg 与 FFmpegKit 版本获取方法
- 移除 EzConvert 内的 Logcat 可用性检测，避免与 LogcatRecorder 重复检测
- 移除 UpdateChecker 外层的冗余判断
- 修改 构建逻辑，构建 debug 时仅构建 arm64-v8a 架构 apk，提升 debug 构建速度
- 修改 app/build.gradle 与 gradle/versioning.gradle 中的 git 使用方法 (适配Gradle9.0)
- 升级 targetSdk/compileSdk 为 35
- 升级 Gradle 为 9.0.0
- 升级 agp 为 8.13.0