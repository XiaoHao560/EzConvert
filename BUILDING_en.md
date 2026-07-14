# Build Guide

> If you are attempting to build this project, please read this document carefully first.
>
> Because some parts of this project are dynamically added during compilation via the **GitHub Actions** environment, **you will definitely encounter the following issues if you clone this project directly and try to build it**.
>
> Please address the following sections one by one.

---

## Table of Contents

1. [App Signing](#app-signing)
2. [ffmpeg-kit-next](#ffmpeg-kit-next)
3. [Google Firebase](#google-firebase)
4. [Notes](#notes)

---

## App Signing

### 1. Create a Digital Signature

For information on **how to create an Android app signing key**, please refer to the [official Android documentation](https://developer.android.com/studio/publish/app-signing).

> If you already have your own app signing key, you can skip this step.

### 2. Configure the Signing Files

1. Create a `key` folder in the project root directory.
2. Place your keystore file into this folder, and create a `keystore.properties` file.
3. Fill in the signing information in `keystore.properties` (replace the `***` in the example with your actual values):

```properties
storePassword=***
keyPassword=***
keyAlias=***
storeFile=***
```

---

## ffmpeg-kit-next

> Since storing prebuilt `ffmpeg-kit-next` AAR packages directly in the project may involve **copyright and patent** issues, you need to build the **full-gpl** version of [ffmpeg-kit-next](https://github.com/arthenica/ffmpeg-kit-next) locally when building this project from source.

### 1. Clone and Build

1. Clone the [ffmpeg-kit-next](https://github.com/arthenica/ffmpeg-kit-next) repository to your local machine.
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
> **Note**: Building ffmpeg-kit-next takes a long time (approximately 30–60 minutes). Please be patient.

### 2. Copy the Build Artifacts

After the build is complete, according to the ffmpeg-kit-next documentation, copy the contents of the `prebuilt` directory to the `libs` directory in the root of this project:

```
EzConvert/libs/com/arthenica/ffmpeg-kit-next/8.1.0/ffmpeg-kit-next-8.1.0.aar
EzConvert/libs/com/arthenica/ffmpeg-kit-next/8.1.0/ffmpeg-kit-next-8.1.0.pom
```

---

## Google Firebase

> If you do not need Google Firebase-related components, it is recommended to directly remove the Firebase configuration from the project.

### 1. Remove Gradle Plugins and Dependencies

Open `app/build.gradle` and remove the following:

**Plugins at the top:**
```gradle
id 'com.google.gms.google-services'
id 'com.google.firebase.crashlytics'
id 'com.google.firebase.firebase-perf'
```

**Dependencies at the bottom:**
```gradle
implementation platform('com.google.firebase:firebase-bom:34.14.0')
implementation 'com.google.firebase:firebase-analytics'
implementation 'com.google.firebase:firebase-crashlytics-ndk'
implementation 'com.google.firebase:firebase-perf'
```

### 2. Remove Related Code

After removing the Firebase-related code, you may encounter build errors. Please modify the following two files and remove any code related to Google Firebase:

- `app/src/main/java/com/tech/ezconvert/ui/MoreSettingsActivity.java`
- `app/src/main/java/com/tech/ezconvert/utils/EzConvert.java`

> The specific modifications depend on the code. If you are unable to make these changes yourself, you can use an AI tool to assist with modifying these two files.

---

## Notes

Once you have completed the above preparations, you can build this project:

- **Debug mode**: Builds only the **arm64-v8a** ABI.
- **Release mode**: Builds both **armeabi-v7a** and **arm64-v8a** ABIs.
