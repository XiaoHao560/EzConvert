package com.tech.ezconvert.manager;

import android.net.Uri;

import com.tech.ezconvert.utils.ParameterData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 管理 MainActivity 的转换队列状态，不包含 UI 或 Android 生命周期逻辑
 */
public class ConversionQueueManager {
    private final List<String> selectedFilePaths = new ArrayList<>();
    private final Map<String, Uri> pathToUriMap = new HashMap<>();
    private final CopyOnWriteArrayList<String> completedOutputFiles = new CopyOnWriteArrayList<>();

    // 当前正在处理的队列下标，只有真正进入下一项时才递增
    private int currentIndex = 0;
    private String taskType = "";
    private boolean syncMode = false;
    private ParameterData syncParams;

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
    }

    public synchronized void clearSelection() {
        selectedFilePaths.clear();
        pathToUriMap.clear();
        currentIndex = 0;
    }

    public synchronized void clearCompletedOutputs() {
        completedOutputFiles.clear();
    }

    /** 重置一次转换流程产生的临时状态，但不会自动删除已经选择的文件 */
    public synchronized void resetProcessingState() {
        currentIndex = 0;
        taskType = "";
        syncMode = false;
        syncParams = null;
        completedOutputFiles.clear();
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
    }

    public synchronized String getTaskType() {
        return taskType;
    }

    public synchronized void setTaskType(String taskType) {
        this.taskType = taskType == null ? "" : taskType;
    }

    public synchronized boolean isSyncMode() {
        return syncMode;
    }

    /**
     * 设置批量同步模式，同步模式下后续文件可以复用同一组参数，避免重复弹出参数对话框
     */
    public synchronized void setSyncMode(boolean syncMode, ParameterData params) {
        this.syncMode = syncMode;
        this.syncParams = syncMode ? params : null;
    }

    public synchronized ParameterData getSyncParams() {
        return syncParams;
    }

    public CopyOnWriteArrayList<String> getCompletedOutputFiles() {
        return completedOutputFiles;
    }

    public void addCompletedOutput(String path) {
        if (path != null && !path.isEmpty()) completedOutputFiles.add(path);
    }
}
