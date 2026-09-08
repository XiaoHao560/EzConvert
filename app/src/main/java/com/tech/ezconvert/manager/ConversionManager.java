package com.tech.ezconvert.manager;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.core.content.ContextCompat;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * WorkManager 转换任务的生命周期管理，Activity 只负责接收结果并更新 UI
 */
public class ConversionManager {
    public interface Listener {
        void onProgress(int progress, long time);
        void onCompleted(boolean success, String message, String outputPath);
        void onRestored(WorkInfo workInfo);
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
    /** 被用户主动取消的任务 ID，其终态回调不能再驱动 Activity 的队列 */
    private UUID ignoredCancelledWorkId;

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
                .putInt(FfmpegWorker.KEY_TASK_INDEX, queue.getCurrentPosition())
                .putInt(FfmpegWorker.KEY_TOTAL_TASKS, queue.size())
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(FfmpegWorker.class)
                .setInputData(inputData)
                .addTag(WORK_TAG_QUEUE)
                .addTag(WORK_TAG_CURRENT)
                .build();

        currentWorkId = workRequest.getId();
        observe(currentWorkId, owner);
        workManager.enqueue(workRequest);
    }

    /**
     * 只在 Activity 启动时做一次“快照式”恢复检查
     *
     * 不能长期 observe WORK_TAG_CURRENT：新提交的 Worker 也带有这个 tag，
     * 长期观察会把刚刚提交的正常任务误判成“恢复任务”，并且每次 WorkInfo 更新
     * 都会重复触发恢复 UI
     */
    public void restoreRunningWorker(LifecycleOwner owner) {
        final com.google.common.util.concurrent.ListenableFuture<List<WorkInfo>> future =
                workManager.getWorkInfosByTag(WORK_TAG_CURRENT);

        future.addListener(() -> {
            final List<WorkInfo> workInfos;
            try {
                workInfos = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e("ConversionManager", "读取后台任务状态被中断", e);
                return;
            } catch (ExecutionException e) {
                Log.e("ConversionManager", "读取后台任务状态失败", e);
                return;
            }

            WorkInfo activeWork = null;
            for (WorkInfo info : workInfos) {
                WorkInfo.State state = info.getState();
                if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED ||
                        state == WorkInfo.State.BLOCKED) {
                    activeWork = info;
                    break;
                }
            }

            final WorkInfo restored = activeWork;
            ContextCompat.getMainExecutor(context).execute(() -> {
                if (restored != null) {
                    currentWorkId = restored.getId();
                    observe(currentWorkId, owner);
                    listener.onRestored(restored);
                } else {
                    listener.onIdleAfterRestore();
                }

                // 已结束的历史任务不需要长期保留。这里仅在启动恢复检查完成后清理
                workManager.pruneWork();
            });
        }, ContextCompat.getMainExecutor(context));
    }

    /**
     * 取消当前 Worker，先记录主动取消的 ID，防止随后收到 CANCELLED 状态时重复通知 Activity
     */
    public void cancelCurrent() {
        if (currentWorkId != null) {
            ignoredCancelledWorkId = currentWorkId;
            workManager.cancelWorkById(currentWorkId);
            currentWorkId = null;
        }
    }

    /** Activity 销毁或离开页面时移除当前 Worker 的 LiveData observer */
    public void clearObservation(LifecycleOwner owner) {
        if (currentWorkLiveData != null) currentWorkLiveData.removeObservers(owner);
    }

    private void observe(UUID workId, LifecycleOwner owner) {
        // Activity 在切换到下一个队列任务时会再次调用 observe，必须主动移除旧 observer，
        // 否则同一个 Activity 会同时监听多个历史 Worker，取消时尤其容易出现重复回调
        if (currentWorkLiveData != null) {
            currentWorkLiveData.removeObservers(owner);
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
                currentWorkId = null;
                listener.onCompleted(true, context.getString(R.string.status_processing_complete), path);
            } else if (state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED) {
                // 用户主动取消后，Activity 的取消流程负责清理文件和 UI
                // 不再让 Worker 的终态回调再次触发“操作已取消”，避免重复日志/Toast
                if (workId.equals(ignoredCancelledWorkId)) {
                    ignoredCancelledWorkId = null;
                    if (workId.equals(currentWorkId)) currentWorkId = null;
                    return;
                }

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
                currentWorkId = null;
                listener.onCompleted(false, error, null);
            }
        });
    }
}
