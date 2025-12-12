package com.tech.ezconvert.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.tech.ezconvert.ui.HomeFragment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PermissionManager {
    
    private static final String TAG = "PermissionManager";
    
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }
    
    // 检查是否拥有所有必要权限
    public static boolean checkAllPermissions(Context context) {
        return checkBasicPermissions(context) && 
               checkMediaPermissions(context) && 
               checkStorageAccess(context);
    }
    
    // 检查是否有基本的媒体访问权限（用于决定按钮是否可用）
    public static boolean hasMediaAccessPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要媒体权限
            return checkMediaPermissions(context);
        } else {
            // Android 12以下需要存储权限
            return checkBasicPermissions(context);
        }
    }
    
    // 请求必要的权限（初次启动时申请）
    public static void requestInitialPermissions(Activity activity, int requestCode, PermissionCallback callback) {
        Log.d(TAG, "请求初始权限（Activity）");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 直接请求媒体权限
            requestMediaPermissions(activity, requestCode, callback);
        } else {
            // Android 12以下请求存储权限
            requestBasicPermissions(activity, requestCode, callback);
        }
    }
    
    // 请求必要的权限（初次启动时申请）
    public static void requestInitialPermissions(Fragment fragment, int requestCode, PermissionCallback callback) {
        Log.d(TAG, "请求初始权限（Fragment）");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 直接请求媒体权限
            requestMediaPermissions(fragment, requestCode, callback);
        } else {
            // Android 12以下请求存储权限
            requestBasicPermissions(fragment, requestCode, callback);
        }
    }
    
    // 请求媒体权限（Android 13+）
    private static void requestMediaPermissions(Activity activity, int requestCode, PermissionCallback callback) {
        List<String> permissionsToRequest = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VIDEO) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO);
        }
        
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
        }
        
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "请求媒体权限: " + permissionsToRequest);
            ActivityCompat.requestPermissions(activity, 
                permissionsToRequest.toArray(new String[0]), requestCode);
        } else {
            Log.d(TAG, "已有所有媒体权限");
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }
    
    // 请求媒体权限（Android 13+）
    private static void requestMediaPermissions(Fragment fragment, int requestCode, PermissionCallback callback) {
        List<String> permissionsToRequest = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.READ_MEDIA_VIDEO) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO);
        }
        
        if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
        }
        
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "请求媒体权限: " + permissionsToRequest);
            fragment.requestPermissions(permissionsToRequest.toArray(new String[0]), requestCode);
        } else {
            Log.d(TAG, "已有所有媒体权限");
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }
    
    // 请求基础存储权限（Android 12以下）
    private static void requestBasicPermissions(Activity activity, int requestCode, PermissionCallback callback) {
        List<String> permissionsToRequest = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "请求基础权限: " + permissionsToRequest);
            ActivityCompat.requestPermissions(activity, 
                permissionsToRequest.toArray(new String[0]), requestCode);
        } else {
            Log.d(TAG, "已有所有基础权限");
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }
    
    // 请求基础存储权限（Android 12以下）
    private static void requestBasicPermissions(Fragment fragment, int requestCode, PermissionCallback callback) {
        List<String> permissionsToRequest = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "请求基础权限: " + permissionsToRequest);
            fragment.requestPermissions(permissionsToRequest.toArray(new String[0]), requestCode);
        } else {
            Log.d(TAG, "已有所有基础权限");
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }
    
    // 查看缺失的权限列表
    public static List<String> getMissingPermissions(Context context) {
        List<String> missingPermissions = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 检查媒体权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) 
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            // Android 12以下检查存储权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
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
            return true;
        }
        
        boolean hasVideo = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        boolean hasAudio = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        
        Log.d(TAG, "媒体权限 - 视频: " + hasVideo + ", 音频: " + hasAudio);
        return hasVideo && hasAudio;
    }
    
    // 检查是否真的可以存储
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
    
    // 处理权限请求结果
    public static void handlePermissionResult(Activity activity, int requestCode, 
                                            String[] permissions, int[] grantResults,
                                            PermissionCallback callback) {
        Log.d(TAG, "处理权限请求结果（Activity）");
        
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
                if (callback != null) {
                    callback.onPermissionsGranted();
                }
            } else {
                Log.d(TAG, "部分或全部权限被拒绝");
                if (callback != null) {
                    callback.onPermissionsDenied();
                }
            }
        }
    }
    
    // 处理权限请求结果
    public static void handlePermissionResult(Fragment fragment, int requestCode, 
                                            String[] permissions, int[] grantResults,
                                            PermissionCallback callback) {
        Log.d(TAG, "处理权限请求结果（Fragment）");
        
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
                if (callback != null) {
                    callback.onPermissionsGranted();
                }
            } else {
                Log.d(TAG, "部分或全部权限被拒绝");
                if (callback != null) {
                    callback.onPermissionsDenied();
                }
            }
        }
    }
    
    // 检查当前权限状态
    public static void checkPermissionStatus(Activity activity, PermissionCallback callback) {
        boolean hasRequiredPermissions = hasMediaAccessPermissions(activity);
        
        Log.d(TAG, "权限状态 - 所需权限: " + hasRequiredPermissions);
        
        if (hasRequiredPermissions) {
            // 有权限
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        } else {
            // 没有权限
            if (callback != null) {
                callback.onPermissionsDenied();
            }
            // 申请权限
            requestInitialPermissions(activity, 100, callback);
        }
    }
    
    // 检查当前权限状态
    public static void checkPermissionStatus(Fragment fragment, PermissionCallback callback) {
        boolean hasRequiredPermissions = hasMediaAccessPermissions(fragment.requireContext());
        
        Log.d(TAG, "权限状态 - 所需权限: " + hasRequiredPermissions);
        
        if (hasRequiredPermissions) {
            // 有权限
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        } else {
            // 没有权限
            if (callback != null) {
                callback.onPermissionsDenied();
            }
            // 申请权限
            requestInitialPermissions(fragment, 100, callback);
        }
    }
}