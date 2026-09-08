package com.tech.ezconvert.manager;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.gson.Gson;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.NotificationHelper;
import com.tech.ezconvert.utils.ParameterData;
import com.tech.ezconvert.worker.FfmpegWorker;

import java.io.File;
import java.util.UUID;

/**
 * WorkManager 转换任务的生命周期管理，Activity 只负责接收结果并更新 UI
 */
public class ConversionManager {
    public interface Listener {
        void onProgress(int progress, long time);
        void onCompleted(boolean success, String message, String outputPath);
        void onRestored();
        void onIdleAfterRestore();
    }

    public static final String WORK_TAG_QUEUE = "ezconvert_queue";
    public static final String WORK_TAG_CURRENT = "ezconvert_current";

    private final Context context;
    private final WorkManager workManager;
    private final Gson gson = new Gson();
    private final ConversionQueueManager queue;
    private final Listener listener;
    private LiveData<WorkInfo> currentWorkLiveData;
    private UUID currentWorkId;

    public ConversionManager(Context context, ConversionQueueManager queue, Listener listener) {
        this.context = context.getApplicationContext();
        this.workManager = WorkManager.getInstance(this.context);
        this.queue = queue;
        this.listener = listener;
    }

    /** 返回当前正在监听的 Worker ID；没有任务时返回 null */
    public UUID getCurrentWorkId() {
        return currentWorkId;
    }
    
    /**
     * 创建并提交当前队列项对应的 OneTimeWorkRequest
     * Activity 不直接接触 WorkManager，只把已经准备好的参数交给这里
     */
    public void submitCurrent(ParameterData params, String outputBasePath, LifecycleOwner owner) {
        String inputKey = queue.getCurrentKey();
        Uri fileUri = queue.getCurrentUri();
        String uriString = fileUri != null ? fileUri.toString() : "";
        String fileName = fileUri != null ? FileUtils.getDisplayName(context, fileUri) : new File(inputKey).getName();
        if (fileName == null) fileName = "file";

        // WorkManager Data 只传递可序列化的基础数据，复杂对象 ParameterData 使用 JSON 保存
        Data inputData = new Data.Builder()
                .putString(FfmpegWorker.KEY_INPUT_PATH, inputKey)
                .putString(FfmpegWorker.KEY_INPUT_URI, uriString)
                .putString(FfmpegWorker.KEY_OUTPUT_PATH_BASE, outputBasePath)
                .putString(FfmpegWorker.KEY_PARAMS_JSON, gson.toJson(params))
                .putString(FfmpegWorker.KEY_FILE_NAME, fileName)
                .putString(FfmpegWorker.KEY_SESSION_ID, queue.getSessionId())
                .putInt(FfmpegWorker.KEY_TASK_INDEX, queue.getCurrentPosition())
                .putInt(FfmpegWorker.KEY_TOTAL_TASKS, queue.size())
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(FfmpegWorker.class)
                .setInputData(inputData)
                .addTag(WORK_TAG_QUEUE)
                .addTag(WORK_TAG_CURRENT)
                .addTag("ezconvert_session_" + queue.getSessionId())
                .addTag("ezconvert_task_" + queue.getCurrentPosition())
                .build();

        currentWorkId = workRequest.getId();
        queue.setCurrentWorkId(currentWorkId.toString());
        observe(currentWorkId, owner);
        workManager.enqueue(workRequest);
    }

    public void restoreRunningWorker(LifecycleOwner owner) {
        // 这里只做一次恢复快照，避免持续监听整个 tag 导致旧任务/新任务互相干扰
        LiveData<java.util.List<WorkInfo>> liveData = workManager.getWorkInfosByTagLiveData(WORK_TAG_CURRENT);
        liveData.observe(owner, workInfos -> {
            liveData.removeObservers(owner);
            if (workInfos == null || workInfos.isEmpty()) {
                listener.onIdleAfterRestore();
                return;
            }

            WorkInfo activeInfo = null;
            WorkInfo latestTerminal = null;
            for (WorkInfo info : workInfos) {
                if (!isCurrentSessionWork(info)) continue;
                WorkInfo.State state = info.getState();
                if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED) {
                    activeInfo = info;
                    break;
                }
                if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED) {
                    latestTerminal = info;
                }
            }

            if (activeInfo != null) {
                currentWorkId = activeInfo.getId();
                queue.setCurrentWorkId(currentWorkId.toString());
                syncQueueIndexFromTags(activeInfo);
                observe(currentWorkId, owner);
                listener.onRestored();
                return;
            }

            // Activity 可能在 Worker 完成回调之前被杀死，此时让 Activity 按正常完成流程推进一次队列
            if (latestTerminal != null) {
                Data output = latestTerminal.getOutputData();
                int taskIndex = output.getInt(FfmpegWorker.KEY_TASK_INDEX, -1);
                boolean isCurrentItem = taskIndex > 0
                        && taskIndex <= queue.size()
                        && taskIndex - 1 == queue.getCurrentIndex();
                if (isCurrentItem) {
                    if (latestTerminal.getState() == WorkInfo.State.SUCCEEDED) {
                        String path = output.getString(FfmpegWorker.KEY_OUTPUT_PATH);
                        if (path != null) queue.addCompletedOutput(path);
                        queue.setCurrentWorkId("");
                        currentWorkId = null;
                        listener.onCompleted(true, context.getString(R.string.status_processing_complete), path);
                        return;
                    } else if (latestTerminal.getState() == WorkInfo.State.FAILED) {
                        String error = output.getString(FfmpegWorker.KEY_ERROR_MESSAGE);
                        if (error == null) error = context.getString(R.string.error_unknown);
                        queue.setCurrentWorkId("");
                        currentWorkId = null;
                        listener.onCompleted(false, error, null);
                        return;
                    }
                }
            }

            queue.setCurrentWorkId("");
            currentWorkId = null;
            listener.onIdleAfterRestore();
        });
    }

    private boolean isCurrentSessionWork(WorkInfo info) {
        String sessionId = queue.getSessionId();
        return sessionId != null && !sessionId.isEmpty()
                && info.getTags().contains("ezconvert_session_" + sessionId);
    }

    private void syncQueueIndexFromTags(WorkInfo info) {
        for (String tag : info.getTags()) {
            if (tag != null && tag.startsWith("ezconvert_task_")) {
                try {
                    int position = Integer.parseInt(tag.substring("ezconvert_task_".length()));
                    if (position > 0 && position <= queue.size()) queue.setCurrentIndex(position - 1);
                } catch (NumberFormatException ignored) {
                    Log.w("ConversionManager", "无法解析任务序号: " + tag);
                }
            }
        }
    }

    /**
     * 取消当前 Worker
     */
    public void cancelCurrent() {
        if (currentWorkId != null) {
            workManager.cancelWorkById(currentWorkId);
            queue.setCurrentWorkId("");
            currentWorkId = null;
        }
    }

    /** Activity 销毁或离开页面时移除当前 Worker 的 LiveData observer */
    public void clearObservation(LifecycleOwner owner) {
        if (currentWorkLiveData != null) currentWorkLiveData.removeObservers(owner);
    }

    private void observe(UUID workId, LifecycleOwner owner) {
        if (currentWorkLiveData != null) {
            // Observer 的移除由 LifecycleOwner 的销毁处理，在此处只需替换 LiveData 即可
        }
        currentWorkLiveData = workManager.getWorkInfoByIdLiveData(workId);
        currentWorkLiveData.observe(owner, workInfo -> {
            if (workInfo == null) return;
            WorkInfo.State state = workInfo.getState();
            if (state == WorkInfo.State.RUNNING) {
                Data progress = workInfo.getProgress();
                listener.onProgress(progress.getInt(FfmpegWorker.KEY_PROGRESS, 0),
                        progress.getLong(FfmpegWorker.KEY_TIME, 0));
            } else if (state == WorkInfo.State.SUCCEEDED) {
                Data output = workInfo.getOutputData();
                String path = output.getString(FfmpegWorker.KEY_OUTPUT_PATH);
                if (path != null) {
                    queue.addCompletedOutput(path);
                    NotificationHelper.showCompleteNotification(context, new File(path).getName(), true, "");
                }
                queue.setCurrentWorkId("");
                currentWorkId = null;
                listener.onCompleted(true, context.getString(R.string.status_processing_complete), path);
            } else if (state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED) {
                Data output = workInfo.getOutputData();
                String error = output.getString(FfmpegWorker.KEY_ERROR_MESSAGE);
                if (state == WorkInfo.State.CANCELLED || context.getString(R.string.error_cancelled).equals(error)) {
                    error = context.getString(R.string.error_cancelled);
                } else if (error == null) {
                    error = context.getString(R.string.error_unknown);
                }
                if (!context.getString(R.string.error_cancelled).equals(error)) {
                    NotificationHelper.showCompleteNotification(context, "", false, error);
                }
                queue.setCurrentWorkId("");
                currentWorkId = null;
                listener.onCompleted(false, error, null);
            }
        });
    }
}
