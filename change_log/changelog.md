## [changelog] 2026-02-12

### 新增
- 新增 输出日志功能
- 新增 自定义Log输出
- 新增 TestLog
- 新增 "日志显示界面" 显示 设备/应用 信息

### 修复

### 优化
- 优化 "日志查看界面" UI
- 优化 部分 UI 界面卡片阴影边距

### 变更
- 修改 "关于" 界面版权声明日期
- 修改 应用图标设计为 material icon

### 移除

### 代码变更
- 修改 auto-pr 添加 dev 分支
- 修改 change_log 分类 (按照版本号进行分类)
- 修改 release.yml 支持新版 change_log 分类
- 新增 构建 debug apk 时需要配置签名
  /EzConver/key/目录下放置 keystore.properties 和 key.keystore
  如果没有配置签名文件会默认使用 debug 签名 / release apk 则会报错