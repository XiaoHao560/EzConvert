# 贡献指南

## 提交变更的流程

1. **创建功能分支**：从 `main` 分支创建新分支
   ```bash
   git checkout -b feat/your-feature-name
   ```

2. 编写代码：实现你的功能或修复

3. 更新日志：在 `change_log/changelog.md` 中添加你的变更
   - 找到最新的 `[changelog] YYYY-MM-DD` 区块
   - 在合适的分类（`### 新增`、`### 修复`、`### 优化`）下添加一行
   - 只添加你本次 PR 的变更，不要修改已有内容

   示例：
   
```markdown
   ## [changelog] 2025-0-0
   
   ### 新增
   - 添加了某功能  ← 已存在的旧条目，不要修改
   
   ### 修复
   - 修复了旧 bug  ← 已存在的旧条目，不要修改
   - 修复某bug  ← 添加你的新条目
   ```

4. 提交推送：
   
```bash
   git add .
   git commit -m "feat: 实现XX功能"
   git push origin feat/your-feature-name
   ```

5. 自动创建 PR：GitHub Actions 会自动创建 PR，并从你的 changelog 条目生成描述

注意事项

- 不要在 PR 中修改不相关的 changelog 条目
- 不要删除已有的 changelog 内容
- 如果改动很小（如修改 README等文档更新），可以省略 changelog 更新

Changelog 分类说明

- 新增：新功能、新接口
- 修复：Bug 修复
- 优化：性能改进、代码重构
- 文档：仅文档更新（可选）