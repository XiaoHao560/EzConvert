#include "common.h"

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_example_imagecodec_ImageCodec_nativeDecode(
        JNIEnv* env, jclass clazz,
        jbyteArray input, jintArray outSize) {

    jsize len = env->GetArrayLength(input);
    std::vector<uint8_t> buffer(len);
    env->GetByteArrayRegion(input, 0, len, reinterpret_cast<jbyte*>(buffer.data()));

    RawImage img;
    bool ok = false;

    if (len > 2 && buffer[0] == 0xFF && buffer[1] == 0xD8) {
        ok = decodeJpeg(buffer.data(), len, img);
    } else if (len > 8 && buffer[0] == 0x89 && buffer[1] == 0x50) {
        ok = decodePng(buffer.data(), len, img);
    } else if (len > 12 && buffer[0] == 'R' && buffer[1] == 'I') {
        ok = decodeWebp(buffer.data(), len, img);
    }

    if (!ok || img.rgba.empty()) {
        return nullptr;
    }

    jint sizes[2] = {img.width, img.height};
    env->SetIntArrayRegion(outSize, 0, 2, sizes);

    jbyteArray result = env->NewByteArray(img.rgba.size());
    env->SetByteArrayRegion(result, 0, img.rgba.size(),
                           reinterpret_cast<const jbyte*>(img.rgba.data()));
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_imagecodec_ImageCodec_nativeEncode(
        JNIEnv* env, jclass clazz,
        jbyteArray rgba, jint width, jint height,
        jint format, jint quality) {

    jsize pixelLen = env->GetArrayLength(rgba);
    RawImage img;
    img.width = width;
    img.height = height;
    img.rgba.resize(pixelLen);
    env->GetByteArrayRegion(rgba, 0, pixelLen, reinterpret_cast<jbyte*>(img.rgba.data()));

    std::vector<uint8_t> out;
    bool ok = false;

    switch (format) {
        case 0: ok = encodeJpeg(img, out, quality); break;
        case 1: ok = encodePng(img, out); break;
        case 2: ok = encodeWebp(img, out, quality); break;
    }

    if (!ok || out.empty()) return nullptr;

    jbyteArray result = env->NewByteArray(out.size());
    env->SetByteArrayRegion(result, 0, out.size(), reinterpret_cast<const jbyte*>(out.data()));
    return result;
}

}
