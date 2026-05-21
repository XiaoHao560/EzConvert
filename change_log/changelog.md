## [changelog] 2026-05-21

### 新增
- 新增 `选择文件` 时支持多选文件 #148
- 新增 `接收分享文件` 时支持接收多个分享文件

### 修复
- 修复 应用启动时权限检查重复触发的问题

### 优化
- 优化 `保存设置` 时添加保存防抖机制

### 变更
- 修改 应用安卓版本为 **Android15**

### 移除

### 代码变更
- 移除 UpdateChecker 外层的冗余判断
- 修改 app/build.gradle 与 gradle/versioning.gradle 中的 git 使用方法 (适配Gradle9.0)
- 升级 targetSdk/compileSdk 为 35
- 升级 Gradle 为 9.0.0
- 升级 agp 为 8.13.0