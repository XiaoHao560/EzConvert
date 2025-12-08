## [changelog] 2025-0-0

### 新增
- 主界面滚动条淡出
- 自动检测更新
- ### 构建相关
    - 添加 SourceFile 和 LineNumberTable
    - 模块化开发
    - 集中库管理依赖版本
    - github：自动 PR
    > 当分支名是 (feat/ fix/ release/ docs/) 时会自动创建PR
    >
    > PR 标题以及信息需要手动前往 PR 界面修改
    - 重构项目结构
    > 根据功能分包（UI层/处理层/工具层），提升编译速度