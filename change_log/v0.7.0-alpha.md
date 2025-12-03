## [changelog] 2025-0-0

### 新增
- 添加了本地构建 release 签名和相关优化
> 需要添加 key/keystore.properties 和 key/"keyname".keystore 两个文件
>
> 否则会构建失败
>
> "keyname" 可自定义

### 优化
- 优化了**转码设置**界面Switch滑动开关
> 使用了 MaterialSwitch

### 变更
- 修改**预发布版本**构建变体为Release，并使用签名