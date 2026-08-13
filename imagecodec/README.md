# ImageCodec

一个基于 NDK 的 Android 图片编解码库，**不依赖 Android 系统版本**，支持 JPEG、PNG、WebP 三种格式的无损/有损转换。所有编解码逻辑通过 `libjpeg-turbo`、`libpng`、`libwebp` 在 Native 层完成，可在 Android 5.0（API 21）及以上所有设备上获得一致的图片格式支持。

## 特性

- **跨版本兼容**：不依赖 `Bitmap.CompressFormat`，Android 5.0 ~ 16 全版本支持 WebP、PNG、JPEG 互转
- **16KB Page Size 兼容**：使用 NDK r28+ 编译，满足 Android 15+ 和 Google Play 上架要求
- **高性能**：基于 `libjpeg-turbo`（SIMD 优化）和 `libwebp` 官方实现，编码/解码速度优于系统 `BitmapFactory`
- **纯 Java API**：通过 JNI 封装，上层调用无需关心 Native 实现
- **轻量**：按需编译，仅包含三种格式支持，AAR 体积约 300~600KB（per ABI）

## 支持格式

| 格式 | 解码 | 编码 | 说明 |
|------|------|------|------|
| JPEG | ✅ | ✅ | 基于 libjpeg-turbo，支持质量调节 |
| PNG | ✅ | ✅ | 基于 libpng，无损编码 |
| WebP | ✅ | ✅ | 基于 libwebp，支持有损（quality < 100）和无损（quality = 100）|

## API 文档

### 常量定义

```java
public static final int FORMAT_JPEG_JPEG = 0;  // JPEG 格式
public static final int FORMAT_PNG  = 1;  // PNG 格式（无损）
public static final int FORMAT_WEBP = 2;  // WebP 格式
```

### 方法列表

1. decode

```java
public static Bitmap decode(byte[] data)
```

功能：将任意支持的图片格式（JPEG/PNG/WebP）解码为 `Bitmap`。

参数：
- `data`：图片文件的二进制数据

返回：
- 解码成功返回 `Bitmap`（配置为 `ARGB_8888`）
- 解码失败（格式不支持或数据损坏）返回 `null`

示例：

```java
byte[] imageBytes = readFileToBytes("input.webp");
Bitmap bitmap = ImageCodec.decode(imageBytes);
if (bitmap != null) {
    imageView.setImageBitmap(bitmap);
}
```

---

2. encode

```java
public static byte[] encode(Bitmap bitmap, int format, int quality)
```

功能：将 `Bitmap` 编码为指定格式的图片文件。

参数：
- `bitmap`：待编码的位图，必须为 `ARGB_8888` 配置
- `format`：目标格式，取值为 `FORMAT_JPEG`、`FORMAT_PNG`、`FORMAT_WEBP`
- `quality`：编码质量
  - `JPEG` / `WebP`：0100，数值越大质量越高，文件越大
  - `PNG`：该参数被忽略，PNG 始终为无损编码
  - `WebP`：当 `quality >= 100` 时启用无损模式

返回：
- 编码成功返回图片文件的 `byte[]`
- 编码失败返回 `null`

示例：

```java
// Bitmap 转 JPEG，质量 90
byte[] jpegBytes = ImageCodec.encode(bitmap, ImageCodec.FORMAT_JPEG, 90);

// Bitmap 转 PNG（无损，quality 参数可填任意值）
byte[] pngBytes = ImageCodec.encode(bitmap, ImageCodec.FORMAT_PNG, 100);

// Bitmap 转 WebP 有损，质量 85
byte[] webpBytes = ImageCodec.encode(bitmap, ImageCodec.FORMAT_WEBP, 85);

// Bitmap 转 WebP 无损
byte[] webpLossless = ImageCodec.encode(bitmap, ImageCodec.FORMAT_WEBP, 100);
```

---

3. convert

```java
public static byte[] convert(byte[] input, int outputFormat, int quality)
```

功能：格式转换，将任意支持的输入格式转换为目标格式。

参数：
- `input`：源图片文件的二进制数据
- `outputFormat`：目标格式
- `quality`：编码质量（规则同 `encode`）

返回：
- 转换成功返回目标格式的 `byte[]`
- 失败返回 `null`

示例：

```java
// JPEG 转 PNG（无损）
byte[] pngBytes = ImageCodec.convert(jpegBytes, ImageCodec.FORMAT_PNG, 100);

// PNG 转 WebP 有损
byte[] webpBytes = ImageCodec.convert(pngBytes, ImageCodec.FORMAT_WEBP, 85);

// WebP 转 JPEG
byte[] jpegBytes = ImageCodec.convert(webpBytes, ImageCodec.FORMAT_JPEG, 90);
```

---

### 完整使用示例

```java
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Thread(() -> {
            try {
                // 1. 读取源文件
                byte[] input = readAssets("sample.jpg");

                // 2. 转换为 WebP（质量 85）
                byte[] webpOutput = ImageCodec.convert(input, ImageCodec.FORMAT_WEBP, 85);
                if (webpOutput != null) {
                    saveToFile(webpOutput, "output.webp");
                }

                // 3. 解码为 Bitmap 预览
                Bitmap bitmap = ImageCodec.decode(input);
                runOnUiThread(() -> imageView.setImageBitmap(bitmap));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private byte[] readAssets(String fileName) throws IOException {
        try (InputStream is = getAssets().open(fileName)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    private void saveToFile(byte[] data, String fileName) throws IOException {
        File file = new File(getExternalFilesDir(null), fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }
}
```

## 实现原理

架构图

```
┌─────────────────────────────────────────┐
│           Java 层 (ImageCodec)          │
│  decode() / encode() / convert()        │
└─────────────────────────────────────────┘
                    │
                    ▼ JNI
┌─────────────────────────────────────────┐
│           Native 层 (C/C++)            │
│  image_codec.cpp  ──  格式识别 & 分发   │
│     ├── jpeg_wrapper.cpp (libjpeg-turbo)│
│     ├── png_wrapper.cpp  (libpng)       │
│     └── webp_wrapper.cpp (libwebp)      │
└─────────────────────────────────────────┘
                    │
                    ▼ 统一中间格式
              ┌──────────┐
              │  RGBA    │
              │  Buffer  │
              └──────────┘
```

### 核心实现

1. 格式识别：通过文件 Magic Number 自动识别输入格式
   - JPEG: `FF D8`
   - PNG: `89 50`
   - WebP: `52 49` ("RI")

2. 统一中间格式：所有解码器输出统一转换为 RGBA8888 内存布局，编码器从 RGBA 读取。这种设计使得任意格式之间可以无损转换。

3. Native 库集成：
   - libjpeg-turbo 3.0.4：提供 TurboJPEG API，支持 NEON SIMD 加速
   - libpng 1.6.47：标准 PNG 编解码，支持所有颜色类型转换
   - libwebp 1.5.0：Google 官方实现，支持有损/无损编码

4. 16KB Page Size：通过 CMake 链接器标志 `-Wl,-z,max-page-size=16384` 确保 `.so` 文件在 Android 15+ 设备上正常加载。

### 线程安全

所有 Native 方法均为无状态函数，不依赖全局变量，可在多线程环境中安全调用。建议在后台线程执行编解码操作以避免阻塞 UI。

## 注意事项

1. Bitmap 配置：`encode()` 方法要求输入的 `Bitmap` 必须为 `ARGB_8888` 配置，否则会因像素格式不匹配导致颜色异常。
2. 内存管理：处理大图时注意内存占用。一张 4096×4096 的 RGBA 图片在内存中约占用 64MB，建议在后台线程处理并及时 `recycle()` 不再使用的 Bitmap。
3. 无损转换的物理限制：
   - PNG → JPEG：由于 JPEG 是有损格式，转换后会丢失透明度信息（Alpha 通道被填充为 0xFF）
   - JPEG → PNG：可以无损转换，但无法恢复 JPEG 压缩时已丢失的图像细节
4. WebP 无损：当 `quality >= 100` 时，WebP 编码器自动切换为无损模式，此时文件体积会显著大于有损模式。
5. APK 体积：本库包含 `arm64-v8a` 和 `armeabi-v7a` 两个 ABI 的 `.so` 文件。如需进一步精简，可在 `build.gradle` 中通过 `ndk.abiFilters` 只保留目标 ABI。

## License

本模块采用与所依赖开源库兼容的许可证发布。底层依赖：
- libjpeg-turbo: [BSD-style License](https://github.com/libjpeg-turbo/libjpeg-turbo/blob/main/LICENSE.md)
- libpng: [libpng License](http://www.libpng.org/pub/png/src/libpng-LICENSE.txt)
- libwebp: [BSD License](https://chromium.googlesource.com/webm/libwebp/+/refs/heads/main/COPYING)
