package com.tech.ezconvert.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ParameterData;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 管理转换队列状态，并把可恢复状态持久化到 SharedPreferences
 * 不依赖 Activity 生命周期，因此进程重启后仍能恢复队列
 */
public class ConversionQueueManager {
    private static final String TAG = "ConversionQueueManager";
    private static final String PREFS = "conversion_session";
    private static final String KEY_STATE = "state";
    // 多个队列管理器实例共享一份 SharedPreferences，因此需要串行化会话切换和状态写入
    private static final Object PERSISTENCE_LOCK = new Object();

    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final List<String> selectedFilePaths = new ArrayList<>();
    private final Map<String, Uri> pathToUriMap = new HashMap<>();
    private final CopyOnWriteArrayList<String> completedOutputFiles = new CopyOnWriteArrayList<>();

    // 当前正在处理的队列下标，只有真正进入下一项时才递增
    private int currentIndex = 0;
    private String taskType = "";
    private boolean syncMode = false;
    private ParameterData syncParams;
    // 本次批量同步队列实际采用的输出目录覆盖模式，例如 DCIM + 音频确认后锁定为 Download
    private String effectiveOutputModeOverride;
    private String sessionId = "";
    private String currentWorkId = "";
    // 当前任务已完成，但下一项需要用户返回界面设置参数
    private boolean pendingParameterPrompt = false;
    private String pendingFailureMessage = "";

    private static class PersistedState {
        List<String> paths = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        List<String> completedOutputs = new ArrayList<>();
        int currentIndex;
        String taskType;
        boolean syncMode;
        ParameterData syncParams;
        String effectiveOutputModeOverride;
        String sessionId;
        String currentWorkId;
        boolean pendingParameterPrompt;
        String pendingFailureMessage;
    }

    public ConversionQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadState();
    }

    /**
     * 用一次文件选择操作的结果替换当前队列
     * 文件路径（或 key）与 Uri 分开保存，便于后续 Worker 同时兼容两种输入来源
     */
    public synchronized void replaceSelection(List<String> keys, Map<String, Uri> uris) {
        synchronized (PERSISTENCE_LOCK) {
            selectedFilePaths.clear();
            pathToUriMap.clear();
            if (keys != null) selectedFilePaths.addAll(keys);
            if (uris != null) pathToUriMap.putAll(uris);
            currentIndex = 0;
            taskType = "";
            syncMode = false;
            syncParams = null;
            effectiveOutputModeOverride = null;
            completedOutputFiles.clear();
            // 文件选择本身不是可恢复任务：在用户真正开始转换之前，不要持久化所选文件
            // 但保留一个不含文件的 session 标记，阻止旧 Worker 在新任务开始前复活旧状态
            currentWorkId = "";
            pendingParameterPrompt = false;
            pendingFailureMessage = "";
            sessionId = UUID.randomUUID().toString();
            PersistedState marker = new PersistedState();
            marker.sessionId = sessionId;
            prefs.edit().putString(KEY_STATE, gson.toJson(marker)).apply();
        }
    }

    public synchronized void clearSelection() {
        selectedFilePaths.clear();
        pathToUriMap.clear();
        currentIndex = 0;
        currentWorkId = "";
        pendingParameterPrompt = false;
        pendingFailureMessage = "";
        saveState();
    }

    public synchronized void clearCompletedOutputs() {
        completedOutputFiles.clear();
        saveState();
    }

    /** 重置一次转换流程产生的临时状态，但不会自动删除已经选择的文件 */
    public synchronized void resetProcessingState() {
        currentIndex = 0;
        taskType = "";
        syncMode = false;
        syncParams = null;
        effectiveOutputModeOverride = null;
        currentWorkId = "";
        pendingParameterPrompt = false;
        pendingFailureMessage = "";
        completedOutputFiles.clear();
        saveState();
    }

    public synchronized List<String> getSelectedFilePaths() { 
        return new ArrayList<>(selectedFilePaths); 
    }

    public synchronized boolean isEmpty() { 
        return selectedFilePaths.isEmpty(); 
    }

    /** 当前队列是否仍有运行中的 Worker 或等待用户继续设置参数的任务 */
    public synchronized boolean hasActiveWorkOrPendingPrompt() {
        return (currentWorkId != null && !currentWorkId.isEmpty())
                || pendingParameterPrompt
                || (syncMode && syncParams != null && currentIndex < selectedFilePaths.size());
    }

    /** 检查磁盘上的队列会话是否仍属于当前实例 */
    public synchronized boolean isCurrentSession() {
        synchronized (PERSISTENCE_LOCK) {
            return isCurrentSessionLocked();
        }
    }

    private boolean isCurrentSessionLocked() {
        PersistedState state = readPersistedState();
        return state != null && sessionId != null && !sessionId.isEmpty()
                && sessionId.equals(state.sessionId);
    }

    public synchronized int size() {
        return selectedFilePaths.size(); 
    }

    public synchronized String getCurrentKey() {
        if (currentIndex < 0 || currentIndex >= selectedFilePaths.size()) return "";
        return selectedFilePaths.get(currentIndex);
    }

    public synchronized Uri getUri(String key) { 
        return pathToUriMap.get(key); 
    }

    public synchronized Uri getCurrentUri() { 
        return getUri(getCurrentKey()); 
    }

    public synchronized void setCurrentIndex(int index) {
        currentIndex = Math.max(0, index);
        saveState();
    }

    public synchronized int getCurrentIndex() { 
        return currentIndex; 
    }

    public synchronized int getCurrentPosition() { 
        return currentIndex + 1; 
    }

    /** 判断当前文件之后是否还有待处理文件 */
    public synchronized boolean hasNext() {
        return currentIndex + 1 < selectedFilePaths.size(); 
    }

    /** 转到下一个文件，调用方应在当前任务成功后调用 */
    public synchronized void moveToNext() {
        currentIndex++;
        currentWorkId = "";
        saveState();
    }

    /** 原子记录批量任务完成结果并推进下标，保留当前 Worker ID 以支持中断恢复 */
    public synchronized boolean completeBatchItem(String outputPath) {
        synchronized (PERSISTENCE_LOCK) {
            PersistedState state = readPersistedState();
            if (state == null || sessionId == null || !sessionId.equals(state.sessionId)) {
                Log.w(TAG, "拒绝提交旧会话的批量进度，sessionId=" + sessionId);
                return false;
            }
            currentIndex = Math.max(0, Math.min(state.currentIndex, selectedFilePaths.size()));
            completedOutputFiles.clear();
            if (state.completedOutputs != null) completedOutputFiles.addAll(state.completedOutputs);
            if (outputPath != null && !outputPath.isEmpty() && !completedOutputFiles.contains(outputPath)) {
                completedOutputFiles.add(outputPath);
            }
            if (currentIndex < selectedFilePaths.size()) currentIndex++;
            saveState();
            return true;
        }
    }

    /** 同步独立 Worker 保存的批量进度；会话不匹配时不会读取或覆盖其他队列状态 */
    public synchronized boolean refreshWorkerProgressFromPersistence() {
        synchronized (PERSISTENCE_LOCK) {
            PersistedState state = readPersistedState();
            if (state == null || sessionId == null || !sessionId.equals(state.sessionId)) return false;
            currentIndex = Math.max(0, Math.min(state.currentIndex, selectedFilePaths.size()));
            completedOutputFiles.clear();
            if (state.completedOutputs != null) completedOutputFiles.addAll(state.completedOutputs);
            return true;
        }
    }

    public synchronized String getTaskType() {
        return taskType;
    }

    public synchronized void setTaskType(String taskType) {
        this.taskType = taskType == null ? "" : taskType;
        saveState();
    }

    public synchronized boolean isSyncMode() {
        return syncMode;
    }

    /**
     * 设置批量同步模式，同步模式下后续文件可以复用同一组参数，避免重复弹出参数对话框
     */
    public synchronized void setSyncMode(boolean syncMode, ParameterData params) {
        this.syncMode = syncMode;
        this.syncParams = syncMode && params != null ? params.copy() : null;
        saveState();
    }

    public synchronized ParameterData getSyncParams() {
        return syncParams;
    }

    /**
     * 设置本次同步队列的有效输出目录覆盖。设置后，整个队列后续文件复用该目录
     * 不再根据每个文件重复弹出输出目录确认
     */
    public synchronized void setEffectiveOutputModeOverride(String outputMode) {
        effectiveOutputModeOverride = outputMode;
        saveState();
    }

    public synchronized String getEffectiveOutputModeOverride() {
        return effectiveOutputModeOverride;
    }

    public synchronized String getSessionId() {
        return sessionId;
    }

    public synchronized void setCurrentWorkId(String workId) {
        currentWorkId = workId == null ? "" : workId;
        saveState();
    }

    public synchronized String getCurrentWorkId() {
        return currentWorkId;
    }

    /**
     * 原子认领当前 Worker 的终态处理权，避免 Activity 重建后多个 ConversionManager
     * 同时收到同一个 WorkInfo 回调并重复推进队列
     */
    public synchronized boolean tryClaimCurrentWorkCompletion(String workId) {
        synchronized (PERSISTENCE_LOCK) {
            if (!isCurrentSessionLocked() || workId == null || !workId.equals(currentWorkId)) return false;
            currentWorkId = "";
            saveState();
            return true;
        }
    }

    public synchronized void setPendingParameterPrompt(boolean pending) {
        pendingParameterPrompt = pending;
        saveState();
    }

    public synchronized void setPendingFailureMessage(String message) {
        pendingFailureMessage = message == null ? "" : message;
        saveState();
    }

    /** 主界面恢复时读取并消费持久化的后台失败结果 */
    public synchronized String consumePendingFailureMessage() {
        synchronized (PERSISTENCE_LOCK) {
            PersistedState state = readPersistedState();
            if (state != null && sessionId != null && sessionId.equals(state.sessionId)) {
                pendingFailureMessage = state.pendingFailureMessage == null ? "" : state.pendingFailureMessage;
            }
            String message = pendingFailureMessage;
            if (message != null && !message.isEmpty()) {
                pendingFailureMessage = "";
                saveState();
            }
            return message;
        }
    }

    public synchronized void clearPendingFailureMessage() {
        pendingFailureMessage = "";
        saveState();
    }

    public synchronized boolean hasPendingParameterPrompt() {
        return pendingParameterPrompt;
    }

    public synchronized CopyOnWriteArrayList<String> getCompletedOutputFiles() {
        return completedOutputFiles;
    }

    public synchronized void addCompletedOutput(String path) {
        if (path != null && !path.isEmpty() && !completedOutputFiles.contains(path)) {
            completedOutputFiles.add(path);
            saveState();
        }
    }

    private synchronized void saveState() {
        synchronized (PERSISTENCE_LOCK) {
            PersistedState existing = readPersistedState();
            // 只有显式的队列替换流程会更换 session；旧实例不得覆盖新会话
            if (existing != null && existing.sessionId != null && !existing.sessionId.isEmpty()
                    && !existing.sessionId.equals(sessionId)) {
                Log.w(TAG, "忽略旧会话的状态写入，当前=" + sessionId + "，磁盘=" + existing.sessionId);
                return;
            }
            PersistedState state = new PersistedState();
            state.paths.addAll(selectedFilePaths);
            for (String key : selectedFilePaths) {
                Uri uri = pathToUriMap.get(key);
                state.uris.add(uri == null ? "" : uri.toString());
            }
            state.completedOutputs.addAll(completedOutputFiles);
            state.currentIndex = currentIndex;
            state.taskType = taskType;
            state.syncMode = syncMode;
            state.syncParams = syncParams;
            state.effectiveOutputModeOverride = effectiveOutputModeOverride;
            state.sessionId = sessionId;
            state.currentWorkId = currentWorkId;
            state.pendingParameterPrompt = pendingParameterPrompt;
            state.pendingFailureMessage = pendingFailureMessage;
            prefs.edit().putString(KEY_STATE, gson.toJson(state)).apply();
        }
    }

    private PersistedState readPersistedState() {
        String json = prefs.getString(KEY_STATE, null);
        if (json == null || json.isEmpty()) return null;
        try {
            return gson.fromJson(json, PersistedState.class);
        } catch (Exception e) {
            Log.e(TAG, "读取持久化队列状态失败", e);
            return null;
        }
    }

    private synchronized void loadState() {
        // 将队列恢复与会话替换及所有持久化状态变更串行化
        synchronized (PERSISTENCE_LOCK) {
            String json = prefs.getString(KEY_STATE, null);
            if (json == null || json.isEmpty()) return;
            try {
                PersistedState state = gson.fromJson(json, PersistedState.class);
                if (state == null) return;
                selectedFilePaths.clear();
                pathToUriMap.clear();
                completedOutputFiles.clear();
                if (state.paths != null) selectedFilePaths.addAll(state.paths);
                if (state.paths != null && state.uris != null) {
                    for (int i = 0; i < state.paths.size() && i < state.uris.size(); i++) {
                        String uri = state.uris.get(i);
                        if (uri != null && !uri.isEmpty()) {
                            pathToUriMap.put(state.paths.get(i), Uri.parse(uri));
                        }
                    }
                }
                if (state.completedOutputs != null) completedOutputFiles.addAll(state.completedOutputs);
                // 即使 currentIndex == size，也表示整个队列已经完成，恢复时需要保留这一状态
                currentIndex = Math.max(0, Math.min(state.currentIndex, selectedFilePaths.size()));
                taskType = state.taskType == null ? "" : state.taskType;
                syncMode = state.syncMode;
                syncParams = state.syncParams;
                effectiveOutputModeOverride = state.effectiveOutputModeOverride;
                sessionId = state.sessionId == null ? "" : state.sessionId;
                currentWorkId = state.currentWorkId == null ? "" : state.currentWorkId;
                pendingParameterPrompt = state.pendingParameterPrompt;
                pendingFailureMessage = state.pendingFailureMessage == null ? "" : state.pendingFailureMessage;

                // 只有已经创建 Worker 的会话才属于“可恢复任务”
                // 未启动的选择要丢弃，但要保留该 session 的空闲标记，防止旧实例复活旧队列
                if (currentWorkId.isEmpty() && !pendingParameterPrompt && pendingFailureMessage.isEmpty()) {
                    selectedFilePaths.clear();
                    pathToUriMap.clear();
                    completedOutputFiles.clear();
                    currentIndex = 0;
                    taskType = "";
                    syncMode = false;
                    syncParams = null;
                    effectiveOutputModeOverride = null;
                    pendingParameterPrompt = false;
                    pendingFailureMessage = "";
                    if (sessionId.isEmpty()) sessionId = UUID.randomUUID().toString();
                    PersistedState idleMarker = new PersistedState();
                    idleMarker.sessionId = sessionId;
                    prefs.edit().putString(KEY_STATE, gson.toJson(idleMarker)).apply();
                    Log.d(TAG, "发现未启动任务的残留文件选择，已丢弃，并保留空闲会话标记");
                    return;
                }

                Log.d(TAG, "已加载可恢复任务: " + selectedFilePaths.size() + " 个文件, currentIndex=" + currentIndex
                        + ", currentWorkId=" + currentWorkId);
            } catch (Exception e) {
                Log.e(TAG, "恢复队列状态失败", e);
            }
        }
    }
}
