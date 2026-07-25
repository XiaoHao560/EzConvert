## [changelog] 2026-07-13

### 新增
- 适配 `Android 15` 16kb页大小

### 修复
- 修复 `Android 11+` 分区存储导致的 EACCES 权限错误
- 修复 ExoPlayer 无法处理 MainActivity 传递的媒体文件路径导致的 `Source error`
- 修复 没有创建通知渠道导致的崩溃 (issue #184)
- 修复 因为预设系统导致的无法正确处理音频相关任务 (issue #181)
- 修复 `裁剪音频` 以及部分 UI 显示错误的问题 (issue #182)
- 修复 `裁剪音频` 不使用指定的编码器以及码率的问题

### 优化

### 变更
- 修改 Toolbar 为固定
- 修改 `转码设置界面` 部分文本说明
- 迁移项目至 [ffmpeg-kit-next:8.1.0](https://github.com/arthenica/ffmpeg-kit-next) (issue #179)

### 移除

### 代码变更