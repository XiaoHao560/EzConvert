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
    private String sessionId = "";
    private String currentWorkId = "";

    private static class PersistedState {
        List<String> paths = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        List<String> completedOutputs = new ArrayList<>();
        int currentIndex;
        String taskType;
        boolean syncMode;
        ParameterData syncParams;
        String sessionId;
        String currentWorkId;
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
        selectedFilePaths.clear();
        pathToUriMap.clear();
        if (keys != null) selectedFilePaths.addAll(keys);
        if (uris != null) pathToUriMap.putAll(uris);
        currentIndex = 0;
        taskType = "";
        syncMode = false;
        syncParams = null;
        completedOutputFiles.clear();
        currentWorkId = "";
        sessionId = UUID.randomUUID().toString();
        saveState();
    }

    public synchronized void clearSelection() {
        selectedFilePaths.clear();
        pathToUriMap.clear();
        currentIndex = 0;
        currentWorkId = "";
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
        currentWorkId = "";
        completedOutputFiles.clear();
        saveState();
    }

    public synchronized List<String> getSelectedFilePaths() { 
        return new ArrayList<>(selectedFilePaths); 
    }

    public synchronized boolean isEmpty() { 
        return selectedFilePaths.isEmpty(); 
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
        state.sessionId = sessionId;
        state.currentWorkId = currentWorkId;
        prefs.edit().putString(KEY_STATE, gson.toJson(state)).apply();
    }

    private synchronized void loadState() {
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
                    if (uri != null && !uri.isEmpty()) pathToUriMap.put(state.paths.get(i), Uri.parse(uri));
                }
            }
            if (state.completedOutputs != null) completedOutputFiles.addAll(state.completedOutputs);
            currentIndex = Math.max(0, Math.min(state.currentIndex, Math.max(0, selectedFilePaths.size() - 1)));
            taskType = state.taskType == null ? "" : state.taskType;
            syncMode = state.syncMode;
            syncParams = state.syncParams;
            sessionId = state.sessionId == null ? "" : state.sessionId;
            currentWorkId = state.currentWorkId == null ? "" : state.currentWorkId;
            Log.d(TAG, "已恢复队列: " + selectedFilePaths.size() + " 个文件, currentIndex=" + currentIndex);
        } catch (Exception e) {
            Log.e(TAG, "恢复队列状态失败", e);
        }
    }
}
