package com.tech.ezconvert;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PermissionManager {
    
    private static final String TAG = "PermissionManager";
    
    public interface PermissionCallback {
        void onAllPermissionsGranted();
        void onPartialPermissionsGranted();
        void onPermissionsDenied();
    }
    
    // 检查必要权限
    public static boolean checkAllPermissions(Context context) {
        return checkBasicPermissions(context) && 
               checkMediaPermissions(context) && 
               checkStorageAccess(context);
    }
    
    // 请求必要权限
    public static void requestNecessaryPermissions(Activity activity, int requestCode) {
        List<String> permissionsToRequest = getMissingPermissions(activity);
        
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "请求权限: " + permissionsToRequest);
            ActivityCompat.requestPermissions(activity, 
                permissionsToRequest.toArray(new String[0]), requestCode);
        } else if (!checkStorageAccess(activity)) {
            // 没有权限要请求，但存储访问仍受限，请求所有文件访问权限
            requestAllFilesAccess(activity);
        } else {
            Log.d(TAG, "所有权限已授予");
        }
    }
    
    // 获取缺失权限列表
    public static List<String> getMissingPermissions(Context context) {
        List<String> missingPermissions = new ArrayList<>();
        
        // 基础存储权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        
        // 媒体权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) 
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        }
        
        return missingPermissions;
    }
    
    // 检查基础存储权限
    public static boolean checkBasicPermissions(Context context) {
        boolean hasRead = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasWrite = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        
        Log.d(TAG, "基础权限 - 读取: " + hasRead + ", 写入: " + hasWrite);
        return hasRead && hasWrite;
    }
    
    // 检查媒体权限（Android 13+）
    public static boolean checkMediaPermissions(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true; // Android 12及以下不需要媒体权限
        }
        
        boolean hasVideo = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        boolean hasAudio = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        
        Log.d(TAG, "媒体权限 - 视频: " + hasVideo + ", 音频: " + hasAudio);
        return hasVideo && hasAudio;
    }
    
    // 检查实际存储访问能力
    public static boolean checkStorageAccess(Context context) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
            boolean canRead = downloadsDir != null && downloadsDir.canRead();
            boolean canWrite = downloadsDir != null && downloadsDir.canWrite();
            
            Log.d(TAG, "存储访问 - 可读: " + canRead + ", 可写: " + canWrite);
            
            // 检查所有文件访问权限（Android 11+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                boolean hasAllFilesAccess = Environment.isExternalStorageManager();
                Log.d(TAG, "所有文件访问权限: " + hasAllFilesAccess);
                return hasAllFilesAccess || (canRead && canWrite);
            }
            
            return canRead && canWrite;
        } catch (Exception e) {
            Log.e(TAG, "检查存储访问失败", e);
            return false;
        }
    }
    
    // 请求所有文件访问权限 (Android 11+)
    public static void requestAllFilesAccess(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.d(TAG, "请求所有文件访问权限");
                
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivityForResult(intent, 102);
                } catch (Exception e) {
                    Log.e(TAG, "无法打开所有文件访问设置", e);
                    openAppSettings(activity);
                }
            } else {
                Log.d(TAG, "已有所有文件访问权限");
            }
        }
    }
    
    // 打开应用设置界面
    public static void openAppSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "无法打开设置页面", e);
        }
    }
    
    // 处理权限请求结果
    public static void handlePermissionResult(Activity activity, int requestCode, 
                                            String[] permissions, int[] grantResults) {
        Log.d(TAG, "处理权限请求结果");
        
        if (requestCode == 100) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Log.d(TAG, "所有请求的权限已授予");
                if (checkStorageAccess(activity)) {
                    // 权限完全通过
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).onPermissionsGranted();
                    }
                } else {
                    // 需要所有文件访问权限
                    requestAllFilesAccess(activity);
                }
            } else {
                Log.d(TAG, "部分或全部权限被拒绝");
                // 重新检查当前权限状态
                checkPermissionStatus(activity);
            }
        }
    }
    
    // 检查当前权限状态
    public static void checkPermissionStatus(Activity activity) {
        boolean hasBasicPermissions = checkBasicPermissions(activity);
        boolean hasMediaPermissions = checkMediaPermissions(activity);
        boolean hasStorageAccess = checkStorageAccess(activity);
        
        Log.d(TAG, "权限状态 - 基础: " + hasBasicPermissions + 
              ", 媒体: " + hasMediaPermissions + ", 存储访问: " + hasStorageAccess);
        
        if (hasStorageAccess) {
            // 权限完全通过
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).onPermissionsGranted();
            }
        } else if (hasBasicPermissions || hasMediaPermissions) {
            // 有部分权限但无法访问存储，需要申请更多权限
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).updateStatus("部分权限已授予，但需要更多权限来访问文件");
            }
            requestNecessaryPermissions(activity, 100);
        } else {
            // 没有任何权限
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).updateStatus("需要存储权限来访问媒体文件");
            }
            requestNecessaryPermissions(activity, 100);
        }
    }
    
    // 处理所有文件访问权限请求结果
    public static void handleAllFilesAccessResult(Activity activity) {
        Log.d(TAG, "处理所有文件访问权限结果");
        checkPermissionStatus(activity);
    }
}