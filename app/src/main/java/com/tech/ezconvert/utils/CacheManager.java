package com.tech.ezconvert.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import com.tech.ezconvert.utils.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {
    private static final String TAG = "CacheManager";
    private static final String CACHE_SUB_DIR = "processing_cache";
    private static final long MAX_CACHE_SIZE = 2L * 1024 * 1024 * 1024; // 2GB 缓存上限
    private static final ConcurrentHashMap<String, String> activeCacheFiles = new ConcurrentHashMap<>();
    
    // 文件访问性检查结果
    public static class AccessResult {
        public final String usablePath;      // 实际可用的路径（原路径或缓存路径）
        public final boolean isFromCache;    // 是否来自缓存
        public final long originalSize;      // 原始文件大小
        
        public AccessResult(String usablePath, boolean isFromCache, long originalSize) {
            this.usablePath = usablePath;
            this.isFromCache = isFromCache;
            this.originalSize = originalSize;
        }
    }
    
    // 检查文件是否可以直接访问，如果不能则复制到缓存目录下
    public static AccessResult prepareFileForProcessing(Context context, String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) {
            Log.e(TAG, "输入路径为空");
            return null;
        }
        
        File originalFile = new File(inputPath);
        
        // 首先检查文件是否存在
        if (!originalFile.exists()) {
            Log.e(TAG, "文件不存在: " + inputPath);
            return null;
        }
        
        long fileSize = originalFile.length();
        Log.d(TAG, "准备处理文件: " + inputPath + ", 大小: " + formatFileSize(fileSize));
        
        // 检查是否可以直接访问
        if (isFileDirectlyAccessible(inputPath)) {
            Log.d(TAG, "文件可直接访问，使用原始路径");
            return new AccessResult(inputPath, false, fileSize);
        }
        
        // 无法直接访问，需要复制到缓存
        Log.w(TAG, "文件无法直接访问，准备复制到缓存目录");
        String cachePath = copyToCache(context, originalFile);
        
        if (cachePath != null) {
            Log.d(TAG, "文件已复制到缓存: " + cachePath);
            activeCacheFiles.put(cachePath, inputPath); // 记录映射关系
            return new AccessResult(cachePath, true, fileSize);
        } else {
            Log.e(TAG, "复制到缓存失败");
            return null;
        }
    }
    
    // 检查文件是否可被 FFmpeg 直接访问
    // 通过尝试读取文件头来验证
    public static boolean isFileDirectlyAccessible(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        File file = new File(path);
        
        // 基本检查
        if (!file.exists() || !file.canRead()) {
            Log.d(TAG, "文件不存在或不可读: " + path);
            return false;
        }
        
        // 尝试实际读取文件头（验证真正的访问权限）
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] header = new byte[8];
            int read = fis.read(header);
            if (read > 0) {
                // 成功读取，文件可访问
                return true;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "安全限制，无法访问文件: " + path);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "IO错误，无法访问文件: " + path + " - " + e.getMessage());
            return false;
        } finally {
            try {
                if (fis != null) fis.close();
            } catch (IOException ignored) {}
        }
        
        return false;
    }
    
    // 将文件复制到应用的私有缓存目录
    private static String copyToCache(Context context, File sourceFile) {
        if (context == null || sourceFile == null) {
            return null;
        }
        
        // 检查缓存空间
        if (!ensureCacheSpace(context, sourceFile.length())) {
            Log.e(TAG, "缓存空间不足");
            return null;
        }
        
        File cacheDir = new File(context.getCacheDir(), CACHE_SUB_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        // 生成缓存文件名：原始文件名_时间戳.扩展名
        String originalName = sourceFile.getName();
        String fileNameWithoutExt = originalName;
        String extension = "";
        int lastDot = originalName.lastIndexOf('.');
        if (lastDot > 0) {
            fileNameWithoutExt = originalName.substring(0, lastDot);
            extension = originalName.substring(lastDot);
        }
        
        String cacheFileName = fileNameWithoutExt + "_" + System.currentTimeMillis() + extension;
        File cacheFile = new File(cacheDir, cacheFileName);
        
        FileChannel sourceChannel = null;
        FileChannel destChannel = null;
        FileInputStream fis = null;
        FileOutputStream fos = null;
        
        try {
            // 使用 FileChannel 进行零拷贝（如果系统支持）或快速复制
            fis = new FileInputStream(sourceFile);
            fos = new FileOutputStream(cacheFile);
            sourceChannel = fis.getChannel();
            destChannel = fos.getChannel();
            
            long transferred = 0;
            long size = sourceChannel.size();
            long startTime = System.currentTimeMillis();
            
            // 分段复制大文件，避免内存问题
            while (transferred < size) {
                long count = Math.min(size - transferred, 64 * 1024 * 1024); // 每次最多 64MB
                transferred += destChannel.transferFrom(sourceChannel, transferred, count);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, String.format("文件复制完成: %s -> %s (%.1f MB, %d ms)", 
                sourceFile.getAbsolutePath(), 
                cacheFile.getAbsolutePath(),
                size / (1024.0 * 1024.0),
                duration));
            
            return cacheFile.getAbsolutePath();
            
        } catch (IOException e) {
            Log.e(TAG, "复制文件失败: " + e.getMessage(), e);
            // 清理失败的文件
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            return null;
        } finally {
            try {
                if (sourceChannel != null) sourceChannel.close();
                if (destChannel != null) destChannel.close();
                if (fis != null) fis.close();
                if (fos != null) fos.close();
            } catch (IOException ignored) {}
        }
    }
    
    // 确保缓存目录有足够的空间
    private static boolean ensureCacheSpace(Context context, long requiredBytes) {
        File cacheDir = new File(context.getCacheDir(), CACHE_SUB_DIR);
        long currentCacheSize = calculateDirectorySize(cacheDir);
        
        if (currentCacheSize + requiredBytes > MAX_CACHE_SIZE) {
            // 需要清理旧缓存
            Log.w(TAG, "缓存空间不足，当前: " + formatFileSize(currentCacheSize) + 
                  ", 需要: " + formatFileSize(requiredBytes));
            cleanupOldCache(cacheDir, requiredBytes);
            
            // 再次检查
            currentCacheSize = calculateDirectorySize(cacheDir);
            return (currentCacheSize + requiredBytes <= MAX_CACHE_SIZE);
        }
        return true;
    }
    
    // 计算目录总大小
    private static long calculateDirectorySize(File dir) {
        if (!dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                }
            }
        }
        return size;
    }
    
    // 清理旧缓存文件
    private static void cleanupOldCache(File cacheDir, long needToFree) {
        File[] files = cacheDir.listFiles();
        if (files == null || files.length == 0) return;
        
        // 按最后修改时间排序（最旧的在前）
        java.util.Arrays.sort(files, (f1, f2) -> 
            Long.compare(f1.lastModified(), f2.lastModified()));
        
        long freed = 0;
        for (File file : files) {
            if (activeCacheFiles.containsKey(file.getAbsolutePath())) {
                continue; // 跳过正在使用的文件
            }
            
            long fileSize = file.length();
            if (file.delete()) {
                freed += fileSize;
                Log.d(TAG, "清理缓存文件: " + file.getName() + " (" + formatFileSize(fileSize) + ")");
                if (freed >= needToFree) break;
            }
        }
        
        Log.d(TAG, "共清理缓存: " + formatFileSize(freed));
    }
    
    // 处理完成后清理缓存文件
    // 应在 FFmpeg 回调的 onComplete 或者 onError 中调用
    public static void releaseCacheFile(String cachePath) {
        if (cachePath == null || !cachePath.contains(CACHE_SUB_DIR)) {
            return; // 不是缓存文件，不处理
        }
        
        String originalPath = activeCacheFiles.remove(cachePath);
        if (originalPath != null) {
            File cacheFile = new File(cachePath);
            if (cacheFile.exists()) {
                long size = cacheFile.length();
                if (cacheFile.delete()) {
                    Log.d(TAG, "已清理缓存文件: " + cachePath + " (" + formatFileSize(size) + ")");
                } else {
                    Log.w(TAG, "清理缓存文件失败: " + cachePath);
                }
            }
        }
    }
    
    // 清理所有缓存
    public static void cleanupAllCache(Context context) {
        File cacheDir = new File(context.getCacheDir(), CACHE_SUB_DIR);
        if (!cacheDir.exists()) return;
        
        File[] files = cacheDir.listFiles();
        if (files != null) {
            int count = 0;
            long totalSize = 0;
            for (File file : files) {
                if (!activeCacheFiles.containsKey(file.getAbsolutePath())) {
                    long size = file.length();
                    if (file.delete()) {
                        count++;
                        totalSize += size;
                    }
                }
            }
            Log.d(TAG, String.format("清理缓存完成: %d 个文件, 共 %s", 
                count, formatFileSize(totalSize)));
        }
        activeCacheFiles.clear();
    }
    
    // 检查文件是否在缓存目录中
    public static boolean isCacheFile(String path) {
        return path != null && path.contains(CACHE_SUB_DIR) && activeCacheFiles.containsKey(path);
    }
    
    // 获取原始文件路径 (如果提供的是缓存路径)
    public static String getOriginalPath(String cachePath) {
        return activeCacheFiles.get(cachePath);
    }
    
    private static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[Math.min(digitGroups, units.length - 1)]);
    }
}
