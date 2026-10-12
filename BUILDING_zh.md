# 编译指南

> 如果你要尝试构建本项目，请先详细阅读本文档
>
> 因为本项目中有部分内容是通过 **GitHub Actions** 环境在编译时动态添加的，所以**直接 Clone 本项目后构建，将会遇到以下问题**
>
> 请按以下章节逐一处理

---

## 目录

1. [软件签名](#软件签名)
2. [ffmpeg-kit-next](#ffmpeg-kit-next-相关)
3. [Google Firebase 相关](#google-firebase-相关)
4. [提示](#提示)


## 软件签名

### 1. 创建数字签名

关于 **如何创建 Android 软件数字签名**，请参考 [Android 官方文档](https://developer.android.com/studio/publish/app-signing)

> 如果你已有自己的数字签名，可跳过此步骤

### 2. 配置签名文件

1. 在项目根目录创建 `key` 文件夹
2. 将你的数字签名文件放入该文件夹，并创建 `keystore.properties` 文件
3. 在 `keystore.properties` 中填写签名信息（将示例中的 `***` 替换为实际内容）：

```properties
storePassword=***
keyPassword=***
keyAlias=***
storeFile=***
```

---

## ffmpeg-kit-next 相关

对于 ffmpeg-kit-next 你可以选择以下两种方法其中之一

1. [自行构建](#1-自行构建)
2. [下载构建好的 ffmpeg-kit-next AAR](#2-直接下载)

> 注意下载的 AAR 需要包含安卓的 arm64-v8a 与 armeabi-v7a 架构
>
> 同时需要注意其构建使用的命令是否包含了本项目中代码所需的外部库 (full-gpl)
> 
> 否则构建本项目时可能会出现 **报错 或 功能无法使用**

### 1. 自行构建

1. 将 [ffmpeg-kit-next](https://github.com/arthenica/ffmpeg-kit-next) 仓库 Clone 到本地
2. 根据 ffmpeg-kit-next 的构建文档，准备好 **Nix** 环境
3. 使用以下命令进行构建：

```bash
./nix-android.sh -p android-r27d -- \
  --enable-gpl \
  --enable-x264 \
  --enable-x265 \
  --enable-android-media-codec \
  --enable-android-zlib \
  --enable-chromaprint \
  --enable-openh264 \
  --enable-harfbuzz \
  --enable-xvidcore \
  --enable-libvidstab \
  --enable-rubberband \
  --enable-dav1d \
  --enable-fontconfig \
  --enable-freetype \
  --enable-fribidi \
  --enable-gmp \
  --enable-gnutls \
  --enable-kvazaar \
  --enable-lame \
  --enable-libass \
  --enable-libiconv \
  --enable-libilbc \
  --enable-libtheora \
  --enable-libvorbis \
  --enable-libvpx \
  --enable-libwebp \
  --enable-libxml2 \
  --enable-libaom \
  --enable-libogg \
  --enable-libpng \
  --enable-libsvtav1 \
  --enable-vvenc \
  --enable-libjxl \
  --enable-tesseract \
  --enable-liblc3 \
  --enable-giflib \
  --enable-jpeg \
  --enable-opencore-amr \
  --enable-opus \
  --enable-shine \
  --enable-snappy \
  --enable-soxr \
  --enable-speex \
  --enable-twolame \
  --enable-vo-amrwbenc \
  --enable-zimg \
  --disable-lib-srt \
  --disable-arm-v7a \
  --disable-x86 \
  --disable-x86-64
```

> [!NOTE]
> **注意**：ffmpeg-kit-next 的构建耗时较长（约 30–60 分钟），请耐心等待

#### 复制构建产物

构建完成后，根据 ffmpeg-kit-next 的文档，将 `prebuilt` 目录下的内容复制到本项目根目录的 `libs` 目录下：

```
<项目根目录>/libs/com/arthenica/ffmpeg-kit-next/<version>/ffmpeg-kit-next-<version>.aar
<项目根目录>/libs/com/arthenica/ffmpeg-kit-next/<version>/ffmpeg-kit-next-<version>.pom
```

### 2. 直接下载

如果你不想自己构建，并且需要一个 ffmpeg-kit-next AAR 可以前往我的另一个仓库 [(ffmpeg-kit-next)](https://github.com/XiaoHao560/ffmpeg-kit-next) 直接从 Release 中下载最新版本的 ffmpeg-kit-next AAR 即可

当然你也可以选择使用其他人构建好的 AAR 包

> [!NOTE] 
> 如果你一定要使用其他人构建好的 AAR 包，请注意检查他使用的构建命令以及构建出来的产物是否符合以下要求: 
> 
> 1. 使用的构建命令是否启用了所有的外部库 (full-gpl)
> 
> 2. 构建的 AAR 包是否支持 Android 平台，并且是否包含 arm64-v8a 与 armeabi-v7a 架构
> 
> 如果你不确定是否符合以上要求，请直接前往我的另一个仓库的 Release 下载，或者自己构建满足以上要求的 AAR 包使用

---

## Google Firebase 相关

> 如果你不需要 Google Firebase 相关组件，建议直接删除项目中的 Firebase 配置

### 1. 删除 Gradle 插件与依赖

打开 `app/build.gradle`，删除以下内容：

**顶部插件：**
```gradle
id 'com.google.gms.google-services'
id 'com.google.firebase.crashlytics'
id 'com.google.firebase.firebase-perf'
```

**底部依赖：**
```gradle
implementation platform('com.google.firebase:firebase-bom:34.14.0')
implementation 'com.google.firebase:firebase-analytics'
implementation 'com.google.firebase:firebase-crashlytics-ndk'
implementation 'com.google.firebase:firebase-perf'
```

### 2. 删除相关代码

删除 Firebase 相关代码后，构建时会遇到报错，请修改以下两个文件，删除其中与 Google Firebase 相关的代码：

- `app/src/main/java/com/tech/ezconvert/ui/MoreSettingsActivity.java`
- `app/src/main/java/com/tech/ezconvert/utils/EzConvert.java`

> 具体修改方式因代码而异，如无法自行修改，可使用 AI 工具辅助处理。

---

## 提示

完成以上准备后，即可构建本项目：

- **Debug 模式**：仅构建 **arm64-v8a** 架构
- **Release 模式**：同时构建 **armeabi-v7a** 与 **arm64-v8a** 架构
