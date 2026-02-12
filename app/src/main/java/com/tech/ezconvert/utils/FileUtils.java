package com.tech.ezconvert.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import com.tech.ezconvert.utils.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class FileUtils {
    private static final String TAG = "FileUtils";
    
    public static String getPath(Context context, Uri uri) {
        Log.d(TAG, "开始解析URI: " + uri.toString());
        Log.d(TAG, "URI Scheme: " + uri.getScheme() + ", Authority: " + uri.getAuthority());
        
        // 尝试标准 Document URI 解析
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && 
            DocumentsContract.isDocumentUri(context, uri)) {
            
            Log.d(TAG, "检测到 Document URI");
            
            if (isExternalStorageDocument(uri)) {
                Log.d(TAG, "类型: ExternalStorageDocument");
                final String docId = DocumentsContract.getDocumentId(uri);
                Log.d(TAG, "Document ID: " + docId);
                final String[] split = docId.split(":");
                final String type = split[0];
                
                if ("primary".equalsIgnoreCase(type)) {
                    String path = Environment.getExternalStorageDirectory() + "/" + split[1];
                    Log.d(TAG, "ExternalStorage 路径: " + path);
                    return path;
                } else {
                    Log.w(TAG, "非主存储: " + type);
                }
            } else if (isDownloadsDocument(uri)) {
                Log.d(TAG, "类型: DownloadsDocument");
                final String id = DocumentsContract.getDocumentId(uri);
                Log.d(TAG, "Download ID: " + id);
                
                // 尝试直接解析 raw: 路径
                if (id.startsWith("raw:")) {
                    String path = id.replaceFirst("raw:", "");
                    Log.d(TAG, "Raw 路径: " + path);
                    return path;
                }
                
                // 尝试 MediaStore Downloads 查询（Android 10+）
                if (id.contains(":")) {
                    String[] split = id.split(":");
                    String actualId = split[1];
                    Log.d(TAG, "尝试 MediaStore 查询, ID: " + actualId);
                    
                    String mediaPath = getDownloadPathFromMediaStore(context, actualId);
                    if (mediaPath != null) {
                        Log.d(TAG, "MediaStore 查询成功: " + mediaPath);
                        return mediaPath;
                    }
                }
                
                // 回退到传统 content URI 查询
                try {
                    final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    Log.d(TAG, "尝试传统 Downloads URI: " + contentUri);
                    String path = getDataColumn(context, contentUri, null, null);
                    if (path != null) {
                        Log.d(TAG, "传统查询成功: " + path);
                        return path;
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "ID 不是数字: " + id);
                }
                
                // 保底方案，将文件复制到缓存
                Log.w(TAG, "所有查询失败，复制到缓存");
                return copyUriToCache(context, uri);
            } else if (isMediaDocument(uri)) {
                Log.d(TAG, "类型: MediaDocument");
                final String docId = DocumentsContract.getDocumentId(uri);
                Log.d(TAG, "Media Document ID: " + docId);
                final String[] split = docId.split(":");
                final String type = split[0];
                Log.d(TAG, "Media 类型: " + type + ", ID: " + split[1]);
                
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                
                if (contentUri != null) {
                    Log.d(TAG, "Media URI: " + contentUri);
                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};
                    String path = getDataColumn(context, contentUri, selection, selectionArgs);
                    if (path != null) {
                        Log.d(TAG, "Media 路径解析成功: " + path);
                        return path;
                    } else {
                        Log.w(TAG, "Media 路径解析失败");
                    }
                } else {
                    Log.w(TAG, "未知的 Media 类型: " + type);
                }
            } else {
                Log.w(TAG, "未知的 Document 类型: " + uri.getAuthority());
            }
        }
        
        // 处理普通 content URI
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            Log.d(TAG, "处理 Content URI");
            // 尝试查询 _data 列（Android 9 及以下）
            String path = getDataColumn(context, uri, null, null);
            if (path != null) {
                Log.d(TAG, "Content URI 路径解析成功: " + path);
                return path;
            } else {
                Log.w(TAG, "Content URI 无法解析路径，尝试复制到缓存");
            }
            
            // Android 10+：复制到缓存目录
            String cachePath = copyUriToCache(context, uri);
            if (cachePath != null) {
                Log.d(TAG, "Content URI 已复制到缓存: " + cachePath);
                return cachePath;
            } else {
                Log.e(TAG, "Content URI 复制到缓存失败");
            }
        }
        
        // 处理 file URI
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            Log.d(TAG, "File URI 路径: " + path);
            return path;
        }
        
        Log.e(TAG, "无法解析 URI: " + uri);
        return null;
    }
    
    // 从 MediaStore Downloads 获取路径
    private static String getDownloadPathFromMediaStore(Context context, String downloadId) {
        Cursor cursor = null;
        try {
            Uri uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Downloads.DATA};
            String selection = MediaStore.Downloads._ID + "=?";
            String[] selectionArgs = {downloadId};
            
            Log.d(TAG, "查询 MediaStore.Downloads, URI: " + uri);
            
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(MediaStore.Downloads.DATA);
                if (columnIndex >= 0) {
                    String path = cursor.getString(columnIndex);
                    if (path != null && !path.isEmpty()) {
                        File file = new File(path);
                        if (file.exists()) {
                            Log.d(TAG, "MediaStore 路径有效: " + path);
                            return path;
                        } else {
                            Log.w(TAG, "MediaStore 路径不存在: " + path);
                        }
                    } else {
                        Log.w(TAG, "MediaStore 路径为空");
                    }
                } else {
                    Log.w(TAG, "DATA 列不存在");
                }
            } else {
                Log.w(TAG, "MediaStore 查询无结果");
            }
        } catch (Exception e) {
            Log.e(TAG, "MediaStore 查询失败: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }
    
    private static String getDataColumn(Context context, Uri uri, String selection, 
                                      String[] selectionArgs) {
        Log.d(TAG, "查询 DataColumn, URI: " + uri + ", Selection: " + selection);
        Cursor cursor = null;
        final String column = MediaStore.MediaColumns.DATA;
        final String[] projection = {column};
        
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, 
                                                      selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndex(column);
                Log.d(TAG, "Column Index: " + columnIndex);
                // Android 10+ 可能返回 -1，需要检查
                if (columnIndex >= 0) {
                    String path = cursor.getString(columnIndex);
                    if (path != null && !path.isEmpty()) {
                        Log.d(TAG, "DataColumn 路径: " + path);
                        return path;
                    } else {
                        Log.w(TAG, "DataColumn 路径为空");
                    }
                } else {
                    Log.w(TAG, "Column Index 为 -1，Android 10+ 限制");
                }
            } else {
                Log.w(TAG, "Cursor 为空或无法移动");
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件路径失败: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }
    
    /**
     * 将 URI 内容复制到应用缓存目录，返回真实文件路径
     * 适用于 Android 10+ 无法直接获取 _data 列的情况
     */
    private static String copyUriToCache(Context context, Uri uri) {
        Log.d(TAG, "开始复制 URI 到缓存: " + uri);
        FileOutputStream fos = null;
        InputStream is = null;
        try {
            String fileName = getFileName(context, uri);
            Log.d(TAG, "文件名: " + fileName);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "temp_" + System.currentTimeMillis();
                Log.d(TAG, "使用临时文件名: " + fileName);
            }
            
            // 添加扩展名（如果没有）
            if (!fileName.contains(".")) {
                String mimeType = context.getContentResolver().getType(uri);
                Log.d(TAG, "MIME 类型: " + mimeType);
                String ext = getExtensionFromMimeType(mimeType);
                fileName += ext;
                Log.d(TAG, "添加扩展名后: " + fileName);
            }
            
            File cacheDir = new File(context.getCacheDir(), "shared_files");
            if (!cacheDir.exists()) {
                Log.d(TAG, "创建缓存目录: " + cacheDir.getAbsolutePath());
                cacheDir.mkdirs();
            }
            
            File outFile = new File(cacheDir, fileName);
            Log.d(TAG, "目标文件: " + outFile.getAbsolutePath());
            
            // 如果文件已存在且大小相同，直接返回
            if (outFile.exists()) {
                long existingSize = outFile.length();
                long uriSize = getUriFileSize(context, uri);
                Log.d(TAG, "文件已存在, 本地大小: " + existingSize + ", URI大小: " + uriSize);
                if (existingSize == uriSize && uriSize > 0) {
                    Log.d(TAG, "文件大小相同，使用已缓存文件: " + outFile.getAbsolutePath());
                    return outFile.getAbsolutePath();
                } else {
                    Log.d(TAG, "文件大小不同，重新复制");
                }
            }
            
            is = context.getContentResolver().openInputStream(uri);
            if (is == null) {
                Log.e(TAG, "无法打开输入流");
                return null;
            }
            
            fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[8192];
            int length;
            long totalBytes = 0;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
                totalBytes += length;
            }
            
            Log.d(TAG, "文件复制完成: " + outFile.getAbsolutePath() + ", 大小: " + totalBytes + " bytes");
            return outFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "复制文件到缓存失败: " + e.getMessage());
            return null;
        } finally {
            try {
                if (is != null) is.close();
                if (fos != null) fos.close();
            } catch (Exception e) {
                Log.w(TAG, "关闭流失败: " + e.getMessage());
            }
        }
    }
    
    // 从 URI 获取文件名
    private static String getFileName(Context context, Uri uri) {
        Log.d(TAG, "获取文件名, URI: " + uri);
        String result = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    result = cursor.getString(index);
                    Log.d(TAG, "从 OpenableColumns 获取文件名: " + result);
                } else {
                    Log.w(TAG, "DISPLAY_NAME 列不存在");
                }
            } else {
                Log.w(TAG, "无法获取文件名 Cursor");
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件名失败: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }
    
    // 获取 URI 文件大小
    private static long getUriFileSize(Context context, Uri uri) {
        Log.d(TAG, "获取文件大小, URI: " + uri);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    long size = cursor.getLong(sizeIndex);
                    Log.d(TAG, "文件大小: " + size);
                    return size;
                } else {
                    Log.w(TAG, "SIZE 列不存在或为空");
                }
            } else {
                Log.w(TAG, "无法获取文件大小 Cursor");
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件大小失败: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1;
    }
    
    // 根据 MIME 类型获取扩展名
    private static String getExtensionFromMimeType(String mimeType) {
        Log.d(TAG, "MIME 转扩展名: " + mimeType);
        if (mimeType == null) return ".tmp";
        switch (mimeType) {
            case "audio/mpeg": return ".mp3";
            case "audio/wav": return ".wav";
            case "audio/aac": return ".aac";
            case "audio/flac": return ".flac";
            case "audio/ogg": return ".ogg";
            case "audio/mp4": return ".m4a";
            case "video/mp4": return ".mp4";
            case "video/x-matroska": return ".mkv";
            case "video/avi": return ".avi";
            case "video/webm": return ".webm";
            default: 
                Log.w(TAG, "未知 MIME 类型，使用 .tmp");
                return ".tmp";
        }
    }
    
    private static boolean isExternalStorageDocument(Uri uri) {
        boolean result = "com.android.externalstorage.documents".equals(uri.getAuthority());
        if (result) Log.d(TAG, "是 ExternalStorageDocument");
        return result;
    }
    
    private static boolean isDownloadsDocument(Uri uri) {
        boolean result = "com.android.providers.downloads.documents".equals(uri.getAuthority());
        if (result) Log.d(TAG, "是 DownloadsDocument");
        return result;
    }
    
    private static boolean isMediaDocument(Uri uri) {
        boolean result = "com.android.providers.media.documents".equals(uri.getAuthority());
        if (result) Log.d(TAG, "是 MediaDocument");
        return result;
    }
}
