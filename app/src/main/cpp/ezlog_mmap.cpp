#include <jni.h>
#include <string>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <ctime>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "EzLogNative"
#define BUFFER_SIZE (4 * 1024 * 1024)  // 4MB mmap 缓冲区

struct LogPacket {
    char level;
    std::string tag;
    std::string msg;
    long timestamp;
};

class MmapLogger {
private:
    int fd = -1;
    char* mapPtr = nullptr;
    size_t mapSize = BUFFER_SIZE;
    size_t writePos = 0;
    std::string basePath;
    std::string currentFile;
    
    std::queue<LogPacket> queue;
    std::mutex mtx;
    std::condition_variable cv;
    std::thread worker;
    bool running = false;
    
    // 文件索引（滚动用）
    int fileIndex = 0;
    const long MAX_FILE_SIZE = 50 * 1024 * 1024; // 单个文件 50MB
    
public:
    void init(const std::string& path) {
        basePath = path;
        running = true;
        openNewFile();
        worker = std::thread(&MmapLogger::loop, this);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Mmap logger initialized: %s", path.c_str());
    }
    
    void write(char level, const std::string& tag, const std::string& msg) {
        LogPacket pkt;
        pkt.level = level;
        pkt.tag = tag;
        pkt.msg = msg;
        pkt.timestamp = time(nullptr);
        
        {
            std::lock_guard<std::mutex> lock(mtx);
            // 队列上限保护 (超过 1万条丢弃旧日志)
            if (queue.size() > 10000) {
                queue.pop();
            }
            queue.push(pkt);
        }
        cv.notify_one();
    }
    
    void flush() {
        if (mapPtr && fd != -1) {
            msync(mapPtr, writePos, MS_ASYNC); // 异步刷盘
        }
    }
    
    void close() {
        running = false;
        cv.notify_all();
        if (worker.joinable()) worker.join();
        
        if (mapPtr) {
            msync(mapPtr, writePos, MS_SYNC); // 同步刷盘确保不丢
            munmap(mapPtr, mapSize);
            mapPtr = nullptr;
        }
        if (fd != -1) {
            ::close(fd);
            fd = -1;
        }
    }

private:
    void openNewFile() {
        if (mapPtr) {
            msync(mapPtr, writePos, MS_SYNC);
            munmap(mapPtr, mapSize);
        }
        if (fd != -1) ::close(fd);
        
        // 生成文件名：EzConvert_2026-00-00_001.log
        char filename[256];
        time_t now = time(nullptr);
        tm* ltm = localtime(&now);
        snprintf(filename, sizeof(filename), "%s/EzConvert_%04d-%02d-%02d_%03d.log", 
                basePath.c_str(), 1900+ltm->tm_year, 1+ltm->tm_mon, ltm->tm_mday, fileIndex++);
        
        currentFile = filename;
        fd = open(filename, O_RDWR | O_CREAT | O_APPEND, 0666);
        if (fd < 0) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to open file: %s", filename);
            return;
        }
        
        // 预分配文件大小（避免频繁扩展）
        ftruncate(fd, mapSize);
        mapPtr = (char*)mmap(nullptr, mapSize, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        writePos = 0;
        
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Opened new log file: %s", filename);
    }
    
    void loop() {
        while (running) {
            std::unique_lock<std::mutex> lock(mtx);
            cv.wait(lock, [this] { return !queue.empty() || !running; });
            
            while (!queue.empty()) {
                LogPacket pkt = queue.front();
                queue.pop();
                lock.unlock();
                
                // 格式化日志行
                char header[128];
                tm* ltm = localtime(&pkt.timestamp);
                snprintf(header, sizeof(header), "[%04d-%02d-%02d %02d:%02d:%02d] [%c] [%s] ", 
                        1900+ltm->tm_year, 1+ltm->tm_mon, ltm->tm_mday,
                        ltm->tm_hour, ltm->tm_min, ltm->tm_sec,
                        pkt.level, pkt.tag.c_str());
                
                std::string line = std::string(header) + pkt.msg + "\n";
                size_t len = line.length();
                
                // 检查是否需要滚动文件
                if (writePos + len > mapSize || writePos > MAX_FILE_SIZE) {
                    openNewFile();
                }
                
                // 直接内存拷贝
                if (mapPtr && writePos + len <= mapSize) {
                    memcpy(mapPtr + writePos, line.c_str(), len);
                    writePos += len;
                }
                
                lock.lock();
            }
        }
    }
};

// 全局实例
static MmapLogger g_logger;

extern "C" {

JNIEXPORT void JNICALL
Java_com_tech_ezconvert_utils_NativeLogWriter_init(JNIEnv *env, jclass clazz, jstring logDir, jint maxSize) {
    const char *path = env->GetStringUTFChars(logDir, nullptr);
    g_logger.init(std::string(path));
    env->ReleaseStringUTFChars(logDir, path);
}

JNIEXPORT void JNICALL
Java_com_tech_ezconvert_utils_NativeLogWriter_write(JNIEnv *env, jclass clazz, jchar level, jstring tag, jstring msg) {
    const char *tagStr = env->GetStringUTFChars(tag, nullptr);
    const char *msgStr = env->GetStringUTFChars(msg, nullptr);
    g_logger.write((char)level, std::string(tagStr), std::string(msgStr));
    env->ReleaseStringUTFChars(tag, tagStr);
    env->ReleaseStringUTFChars(msg, msgStr);
}

JNIEXPORT void JNICALL
Java_com_tech_ezconvert_utils_NativeLogWriter_flush(JNIEnv *env, jclass clazz) {
    g_logger.flush();
}

JNIEXPORT void JNICALL
Java_com_tech_ezconvert_utils_NativeLogWriter_close(JNIEnv *env, jclass clazz) {
    g_logger.close();
}

}
