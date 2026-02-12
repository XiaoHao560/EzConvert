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
        // 尝试标准 Document URI 解析
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && 
            DocumentsContract.isDocumentUri(context, uri)) {
            
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                
                // Android 10+ 处理 downloads 文档
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "");
                }
                
                try {
                    final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    String path = getDataColumn(context, contentUri, null, null);
                    if (path != null) return path;
                } catch (NumberFormatException e) {
                    // 某些系统返回的不是纯数字ID，尝试复制文件
                    return copyUriToCache(context, uri);
                }
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                
                if (contentUri != null) {
                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};
                    String path = getDataColumn(context, contentUri, selection, selectionArgs);
                    if (path != null) return path;
                }
            }
        }
        
        // 处理普通 content URI
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            // 尝试查询 _data 列（Android 9 及以下）
            String path = getDataColumn(context, uri, null, null);
            if (path != null) return path;
            
            // Android 10+：复制到缓存目录
            return copyUriToCache(context, uri);
        }
        
        // 处理 file URI
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        
        return null;
    }
    
    private static String getDataColumn(Context context, Uri uri, String selection, 
                                      String[] selectionArgs) {
        Cursor cursor = null;
        final String column = MediaStore.MediaColumns.DATA; // 使用常量代替硬编码
        final String[] projection = {column};
        
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, 
                                                      selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndex(column);
                // Android 10+ 可能返回 -1，需要检查
                if (columnIndex >= 0) {
                    String path = cursor.getString(columnIndex);
                    if (path != null && !path.isEmpty()) {
                        return path;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件路径失败", e);
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
        FileOutputStream fos = null;
        InputStream is = null;
        try {
            String fileName = getFileName(context, uri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "temp_" + System.currentTimeMillis();
            }
            
            // 添加扩展名（如果没有）
            if (!fileName.contains(".")) {
                String mimeType = context.getContentResolver().getType(uri);
                String ext = getExtensionFromMimeType(mimeType);
                fileName += ext;
            }
            
            File cacheDir = new File(context.getCacheDir(), "shared_files");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            File outFile = new File(cacheDir, fileName);
            
            // 如果文件已存在且大小相同，直接返回
            if (outFile.exists()) {
                long existingSize = outFile.length();
                long uriSize = getUriFileSize(context, uri);
                if (existingSize == uriSize && uriSize > 0) {
                    Log.d(TAG, "使用已缓存文件: " + outFile.getAbsolutePath());
                    return outFile.getAbsolutePath();
                }
            }
            
            is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            
            fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            
            Log.d(TAG, "文件已复制到缓存: " + outFile.getAbsolutePath());
            return outFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "复制文件到缓存失败", e);
            return null;
        } finally {
            try {
                if (is != null) is.close();
                if (fos != null) fos.close();
            } catch (Exception e) {
                
            }
        }
    }
    
    // 从 URI 获取文件名
    private static String getFileName(Context context, Uri uri) {
        String result = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    result = cursor.getString(index);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件名失败", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }
    
    // 获取 URI 文件大小
    private static long getUriFileSize(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件大小失败", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1;
    }
    
    // 根据 MIME 类型获取扩展名
    private static String getExtensionFromMimeType(String mimeType) {
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
            default: return ".tmp";
        }
    }
    
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }
    
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }
    
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
