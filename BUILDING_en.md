# Build Guide

> If you want to try building this project, please read this document carefully first.
>
> Because some parts of this project are dynamically added at build time through the **GitHub Actions** environment, **building directly after cloning this project will cause the following issues**.
>
> Please handle them one by one according to the following sections.

---

## Table of Contents

1. [App Signing](#app-signing)
2. [ffmpeg-kit-next](#ffmpeg-kit-next-related)
3. [Google Firebase Related](#google-firebase-related)
4. [Tips](#tips)

---

## App Signing

### 1. Create a Digital Signature

For information on **how to create an Android app digital signature**, please refer to the [Android official documentation](https://developer.android.com/studio/publish/app-signing).

> If you already have your own digital signature, you can skip this step.

### 2. Configure Signing Files

1. Create a `key` folder in the project root directory.
2. Put your digital signature file into this folder, and create a `keystore.properties` file.
3. Fill in the signing information in `keystore.properties` (replace `***` in the example with actual values):

```properties
storePassword=***
keyPassword=***
keyAlias=***
storeFile=***
```

---

## ffmpeg-kit-next Related

For ffmpeg-kit-next, you can choose one of the following two methods:

1. [Build it yourself](#1-build-it-yourself)
2. [Download a prebuilt ffmpeg-kit-next AAR](#2-download-directly)

> Note that the downloaded AAR must include the Android arm64-v8a and armeabi-v7a architectures.
>
> Also note whether the build command used includes the external libraries required by the code in this project (full-gpl).
>
> Otherwise, when building this project, you may encounter **errors or unavailable features**.

### 1. Build It Yourself

1. Clone the [ffmpeg-kit-next](https://github.com/arthenica/ffmpeg-kit-next) repository locally.
2. According to the ffmpeg-kit-next build documentation, prepare the **Nix** environment.
3. Build using the following command:

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
> **Note**: Building ffmpeg-kit-next takes a long time (about 30–60 minutes), so please be patient.

#### Copy Build Artifacts

After the build is complete, according to the ffmpeg-kit-next documentation, copy the contents of the `prebuilt` directory to the `libs` directory in the project root:

```
<project root>/libs/com/arthenica/ffmpeg-kit-next/<version>/ffmpeg-kit-next-<version>.aar
<project root>/libs/com/arthenica/ffmpeg-kit-next/<version>/ffmpeg-kit-next-<version>.pom
```

### 2. Download Directly

If you do not want to build it yourself and need an ffmpeg-kit-next AAR, you can go to my other repository [(ffmpeg-kit-next)](https://github.com/XiaoHao560/ffmpeg-kit-next) and download the latest version of the ffmpeg-kit-next AAR directly from Releases.

Of course, you can also choose to use an AAR built by someone else.

> [!NOTE]
> If you insist on using an AAR built by someone else, please check whether the build command they used and the artifact they produced meet the following requirements:
>
> 1. Whether the build command used enabled all external libraries (full-gpl).
>
> 2. Whether the built AAR supports the Android platform and includes the arm64-v8a and armeabi-v7a architectures.
>
> If you are not sure whether the above requirements are met, please download directly from the Releases of my other repository, or build an AAR yourself that meets the above requirements.

---

## Google Firebase Related

> If you do not need Google Firebase-related components, it is recommended to delete the Firebase configuration from the project directly.

### 1. Delete Gradle Plugins and Dependencies

Open `app/build.gradle` and delete the following content:

**Top plugins:**
```gradle
id 'com.google.gms.google-services'
id 'com.google.firebase.crashlytics'
id 'com.google.firebase.firebase-perf'
```

**Bottom dependencies:**
```gradle
implementation platform('com.google.firebase:firebase-bom:34.14.0')
implementation 'com.google.firebase:firebase-analytics'
implementation 'com.google.firebase:firebase-crashlytics-ndk'
implementation 'com.google.firebase:firebase-perf'
```

### 2. Delete Related Code

After deleting Firebase-related code, you will encounter build errors. Please modify the following two files and delete the Google Firebase-related code in them:

- `app/src/main/java/com/tech/ezconvert/ui/MoreSettingsActivity.java`
- `app/src/main/java/com/tech/ezconvert/utils/EzConvert.java`

> The specific modifications vary depending on the code. If you cannot modify it yourself, you can use AI tools to assist.

---

## Tips

After completing the above preparations, you can build this project:

- **Debug mode**: builds only the **arm64-v8a** architecture.
- **Release mode**: builds both the **armeabi-v7a** and **arm64-v8a** architectures.