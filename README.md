![GitHub release (latest by date)](https://img.shields.io/github/v/release/XiaoHao560/EzConvert?style=flat-square)
![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/XiaoHao560/EzConvert?include_prereleases&style=flat-square&label=pre-release&color=orange)
![CI](https://github.com/XiaoHao560/EzConvert/workflows/ci/badge.svg)
![GitHub all releases](https://img.shields.io/github/downloads/XiaoHao560/EzConvert/total?style=flat-square)
![Beta](https://img.shields.io/badge/status-beta-yellow.svg)
![GitHub top language](https://img.shields.io/github/languages/top/XiaoHao560/EzConvert?style=flat-square&color=informational)
![GitHub repo size](https://img.shields.io/github/repo-size/XiaoHao560/EzConvert?style=flat-square&color=informational)
![License](https://img.shields.io/github/license/XiaoHao560/EzConvert?style=flat-square)
# 项目更新日志
<details markdown='1'><summary>展开/收起</summary>

从 v0.2.0 开始，我们使用**结构化更新日志**[change_log](change_log/)。
此前版本的变更记录请查看 [GitHub Releases](https://github.com/XiaoHao560/EzConvert/releases) 或提交历史。

从 v0.5.1 开始，我们使用**线性历史**的方式合并PR，所有PR均使用 **Rebase & Merge** 的方式合并。

</details>

# 


# EzConvert

基于 FFmpeg 的 Android 转码与提取工具

## 项目简介

EzConvert 是一款运行于安卓平台的转码与媒体内容提取工具，底层集成了强大的 FFmpeg，支持多种音视频格式的转换和提取。功能持续开发中，欢迎反馈问题与建议！

## 功能特点

- 支持主流音视频格式的相互转码
> 视频格式：mp4,avi,mov,mkv,flv,webm,gif
> 
> 音频格式：mp3,wav,aac,flac,ogg,m4a
- 媒体文件内容提取（如音轨、视频流等）
- 简洁易用的安卓界面
- 基于 FFmpeg，转换高效、质量可控
- 持续迭代，欢迎参与测试和反馈

## 版本控制

本项目将遵循**语义化版本控制规范（SemVer）** 来进行控制应用版本号，关于**语义化版本控制规范**请详见 [语义化版本控制规范](https://semver.org/)

## 技术栈

- 核心语言：Java
- 平台：Android
- 音视频处理：FFmpeg

## 安装与运行

1. Clone 本仓库到本地：

   ```bash
   git clone https://github.com/XiaoHao560/EzConvert.git
   ```

2. 使用 Android Studio 或兼容 IDE 导入项目。

3. 配置 FFmpeg 依赖，并连接安卓设备或模拟器运行。

## 使用说明

1. 启动 EzConvert App
2. 选择需要转码或提取的文件
3. 设置目标格式及相关参数（参数已预制，无需手动调整）
4. 一键开始处理，等待转换完成

## 已知问题与计划

> UI/交互仍在持续优化
> 
> MKV,WebM 格式转换不支持硬件编解码
- [x] 硬件编解码/多线程优化
- [x] 转换视频格式
- [x] 转换音频格式
- [x] 压缩视频
- [x] 提取视频
- [x] 提取音频
- [x] 裁剪视频
- [x] 裁剪音频
- [x] 视频截图
- [x] 错误日志输出/全部日志输出
- [x] 日志级别设置
- [x] 从系统分享导入媒体文件
- [x] 检测更新
- [x] 通知设置
- [ ] 自定义文件名功能
- [ ] 自定义 UI 颜色
- [ ] 主题设置
- [ ] 自定义转换质量
- [ ] 预览媒体文件功能
- [ ] 添加内置播放器用于预览
- [ ] 更多媒体格式转换支持
- [ ] 添加更多设置功能
- [ ] 实现真实进度条
- [ ] 媒体文件详细信息显示
- [ ] 单独调整质量参数
- [ ] 音频淡出淡入
- [ ] 添加视频水印
- [ ] 合并视频与音频

 <mark>欢迎提交 Bug 及功能建议！</mark>

## 如何参与贡献

我们欢迎所有形式的贡献！请遵循以下步骤：

1. **阅读贡献指南**：详细流程请查看 [CONTRIBUTING.md](CONTRIBUTING.md)
2. **Fork 本项目** 并创建功能分支
3. **提交 Pull Request**：修改代码后，按照 CONTRIBUTING.md 中的流程提交
4. **提交 Issue**：遇到 Bug 或有功能建议，可通过 [GitHub Issues](https://github.com/XiaoHao560/EzConvert/issues) 反馈

> [!NOTE]
> **提示**：每次提交 PR 前，请在 `change_log/changelog.md` 中记录你的变更，PR 描述会自动生成。

## 许可证
Copyright (C) 2024-2026 XiaoHao560  
本项目采用 [GNU General Public License v3.0](LICENSE) 开源许可证。

> 注：因本项目使用了 FFmpegKit，任何包含本项目的二进制再分发必须遵守 GPL v3开源协议。

## 鸣谢
- [FFmpegKit](https://github.com/arthenica/ffmpeg-kit)


## 联系与反馈

如有疑问或建议，请通过 [GitHub Issues](https://github.com/XiaoHao560/EzConvert/issues) 联系作者。
