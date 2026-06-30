![GitHub release (latest by date)](https://img.shields.io/github/v/release/XiaoHao560/EzConvert?style=flat-square)
![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/XiaoHao560/EzConvert?include_prereleases&style=flat-square&label=pre-release&color=orange)
![CI](https://github.com/XiaoHao560/EzConvert/workflows/ci/badge.svg)
![GitHub all releases](https://img.shields.io/github/downloads/XiaoHao560/EzConvert/total?style=flat-square)
![Beta](https://img.shields.io/badge/status-beta-yellow.svg)
![GitHub top language](https://img.shields.io/github/languages/top/XiaoHao560/EzConvert?style=flat-square&color=informational)
![GitHub repo size](https://img.shields.io/github/repo-size/XiaoHao560/EzConvert?style=flat-square&color=informational)
![License](https://img.shields.io/github/license/XiaoHao560/EzConvert?style=flat-square)

[中文](README_zh.md)

# Changelog

<details markdown='1'><summary>Expand/Collapse</summary>

Starting from v0.2.0, we use a **structured changelog** [change_log](change_log/).
For earlier versions, please refer to [GitHub Releases](https://github.com/XiaoHao560/EzConvert/releases) or the commit history.

Starting from v0.5.1, we use a **linear history** approach to merge PRs. All PRs are merged via **Rebase & Merge**.

</details>

# EzConvert

An Android transcoding and media extraction tool powered by FFmpeg

## Overview

EzConvert is a transcoding and media extraction tool designed for the Android platform, built on top of the powerful FFmpeg framework. It supports conversion and extraction across a wide range of audio and video formats. Features are actively under development—your feedback and suggestions are welcome!

## Features

- Supports transcoding between mainstream audio and video formats
  - Video formats: mp4, avi, mov, mkv, flv, webm, gif
  - Audio formats: mp3, wav, aac, flac, ogg, m4a
- Supports media content extraction (e.g., audio tracks, video streams)
- Supports video compression
- Supports trimming/cropping of video and audio files
- Supports extracting video frames as images
- Clean and intuitive Material Design 3 UI
- Powered by FFmpeg for efficient conversion with controllable quality
- Continuously updated—welcome to participate in testing and feedback

## Version Control

This project follows the **Semantic Versioning (SemVer)** specification for version numbering. For details, see [SemVer](https://semver.org/).

## Tech Stack

- Core Language: Java
- Platform: Android
- Media Processing: FFmpeg

## Building from Source

1. Clone this repository to your local machine:

   ```bash
   git clone https://github.com/XiaoHao560/EzConvert.git
   ```

2. Import the project using Android Studio or a compatible IDE.

3. Configure the FFmpeg dependency, then connect an Android device or emulator to run.

## Usage

1. Launch the EzConvert app
2. Select the file you want to transcode or extract
3. Set the target format and relevant parameters
4. Tap to start processing and wait for the conversion to complete

## Android Version Support

- This app is designed for **Android 7–15**
  > Not yet adapted for Android 15+ versions.

- Only supports **arm64-v8a** and **armeabi-v7a** architectures. For other architectures, please build the project yourself.

## Known Issues & Roadmap

> UI/UX is still being continuously optimized
>
> MKV and WebM format conversion does not support hardware encoding/decoding

- [x] Hardware encoding/decoding & multi-threading optimization
- [x] Video format conversion
- [x] Audio format conversion
- [x] Video compression
- [x] Video extraction
- [x] Audio extraction
- [x] Video trimming
- [x] Audio trimming
- [x] Video frame capture
- [x] Error log output / full log output
- [x] Log level settings
- [x] Import media files via system share
- [x] Update detection
- [x] Notification settings
- [x] Real progress bar
- [x] Built-in player for preview
- [x] Media file preview
- [x] Settings
- [x] Media file metadata display
- [x] Theme settings
- [x] Custom conversion quality
- [x] Individual quality parameter adjustment
- [ ] Multi-language support
- [ ] Custom filename feature
- [ ] Custom UI colors
- [ ] Support for more media formats
- [ ] Audio fade in/fade out
- [ ] Video watermarking
- [ ] Merge video and audio

<mark>Bug reports and feature suggestions are welcome!</mark>

## Contributing

We welcome all forms of contribution! Please follow the steps below:

1. **Read the Contribution Guide**: For detailed instructions, see [CONTRIBUTING.md](CONTRIBUTING.md)
2. **Fork this repository** and create a feature branch
3. **Submit a Pull Request**: After making changes, submit a PR following the process in CONTRIBUTING.md
4. **Submit an Issue**: For bug reports or feature suggestions, please use [GitHub Issues](https://github.com/XiaoHao560/EzConvert/issues)

> [!NOTE]
> **Note**: Before submitting a PR, please record your changes in `change_log/changelog.md`. The PR description will be generated automatically.

## License

Copyright (C) 2024-2026 XiaoHao560  
This project is licensed under the [GNU General Public License v3.0](LICENSE).

> Note: Since this project uses FFmpegKit, any binary redistribution that includes this project must comply with the GPL v3 open-source license.

## Acknowledgements

- [FFmpegKit](https://github.com/arthenica/ffmpeg-kit)
- [FFmpegKit-16kb](https://github.com/moizhassankh/ffmpeg-kit-android-16KB)

## Contact & Feedback

For questions or suggestions, please reach out via [GitHub Issues](https://github.com/XiaoHao560/EzConvert/issues).

## ☕ Sponsorship

If EzConvert has been helpful to you, your support is greatly appreciated and will motivate continued development and maintenance!

[SPONSOR](SPONSOR.md)