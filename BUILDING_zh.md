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

> 由于直接在项目中存放构建好的 `ffmpeg-kit-next` AAR 包可能存在 **版权和专利** 问题，本地构建本项目时需要自行编译 **full-gpl** 版本的 [ffmpeg-kit-next](https://github.com/arthenica/ffmpeg-kit-next)

### 1. 克隆并构建

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

### 2. 复制构建产物

构建完成后，根据 ffmpeg-kit-next 的文档，将 `prebuilt` 目录下的内容复制到本项目根目录的 `libs` 目录下：

```
EzConvert/libs/com/arthenica/ffmpeg-kit-next/8.1.0/ffmpeg-kit-next-8.1.0.aar
EzConvert/libs/com/arthenica/ffmpeg-kit-next/8.1.0/ffmpeg-kit-next-8.1.0.pom
```

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
