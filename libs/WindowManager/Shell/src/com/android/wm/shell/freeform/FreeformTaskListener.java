/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.freeform;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import static com.android.wm.shell.ShellTaskOrganizer.TASK_LISTENER_TYPE_FREEFORM;

import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.os.Handler;
import android.util.SparseArray;
import android.view.SurfaceControl;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.desktopmode.DesktopModeLoggerTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopRepository;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;
import android.window.WindowContainerTransaction;

import java.io.PrintWriter;
import java.util.Optional;

import android.graphics.Rect;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.app.WindowConfiguration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;
import android.window.WindowContainerTransaction;

/**
 * {@link ShellTaskOrganizer.TaskListener} for {@link
 * ShellTaskOrganizer#TASK_LISTENER_TYPE_FREEFORM}.
 */
public class FreeformTaskListener implements ShellTaskOrganizer.TaskListener,
        ShellTaskOrganizer.FocusListener {
    private static final String TAG = "FreeformTaskListener";

    private final Context mContext;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final Optional<DesktopUserRepositories> mDesktopUserRepositories;
    private final Optional<DesktopTasksController> mDesktopTasksController;
    private final DesktopModeLoggerTransitionObserver mDesktopModeLoggerTransitionObserver;
    private final WindowDecorViewModel mWindowDecorationViewModel;
    private final LaunchAdjacentController mLaunchAdjacentController;
    private final Optional<TaskChangeListener> mTaskChangeListener;
    private final Handler mMainHandler;
    private static final String LOG_TAG = "FreeformTaskListener";

    private final SparseArray<State> mTasks = new SparseArray<>();

    public FreeformTaskListener(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Optional<DesktopTasksController> desktopTasksController,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver,
            LaunchAdjacentController launchAdjacentController,
            WindowDecorViewModel windowDecorationViewModel,
            Optional<TaskChangeListener> taskChangeListener,
            Handler mainHandler) {
        mContext = context;
        mShellTaskOrganizer = shellTaskOrganizer;
        mWindowDecorationViewModel = windowDecorationViewModel;
        mDesktopUserRepositories = desktopUserRepositories;
        mDesktopTasksController = desktopTasksController;
        mDesktopModeLoggerTransitionObserver = desktopModeLoggerTransitionObserver;
        mLaunchAdjacentController = launchAdjacentController;
        mTaskChangeListener = taskChangeListener;
        mMainHandler = mainHandler;
        if (shellInit != null) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    private void onInit() {
        mShellTaskOrganizer.addListenerForType(this, TASK_LISTENER_TYPE_FREEFORM);
        if (DesktopModeStatus.canEnterDesktopMode(mContext)) {
            mShellTaskOrganizer.addFocusListener(this);
        }
    }

    @Override
    public void onTaskAppeared(RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (mTasks.get(taskInfo.taskId) != null) {
            throw new IllegalStateException("Task appeared more than once: #" + taskInfo.taskId);
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Freeform Task Appeared: #%d",
                taskInfo.taskId);
        final State state = new State();
        state.mTaskInfo = taskInfo;
        state.mLeash = leash;
        mTasks.put(taskInfo.taskId, state);

        if (!DesktopModeFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue() &&
                DesktopModeStatus.canEnterDesktopMode(mContext)) {
            mDesktopUserRepositories.ifPresent(userRepositories -> {
                DesktopRepository currentRepo = userRepositories.getProfile(taskInfo.userId);
                currentRepo.addTask(taskInfo.displayId, taskInfo.taskId, taskInfo.isVisible);
            });
        }
        updateLaunchAdjacentController();
        onTaskEnteredFreeform(taskInfo);
    }

    private void resetDpiToSystemDefault(RunningTaskInfo taskInfo) {
        try {
            // Get the system default density
            DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
            int systemDpi = displayMetrics.densityDpi;

            Log.d(TAG, "Resetting DPI for task #" + taskInfo.taskId + " to system default: " + systemDpi);

            // Apply the density change via WindowContainerTransaction
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setDensityDpi(taskInfo.token, systemDpi);

            // Use the shell task organizer reference
            mShellTaskOrganizer.applyTransaction(wct);
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset DPI: " + e.getMessage(), e);
        }
     }

    @Override
    public void onTaskVanished(RunningTaskInfo taskInfo) {
        Log.d(LOG_TAG, "onTaskVanished called");
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Freeform Task Vanished: #%d",
                taskInfo.taskId);

        // Check if it's maximizing to fullscreen
        if (taskInfo.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_FULLSCREEN) {
            Log.d(TAG, "Window maximized to fullscreen! Resetting DPI to system default for task #" +
                    taskInfo.taskId);
            resetDpiToSystemDefault(taskInfo);
        }

        mTasks.remove(taskInfo.taskId);

        if (!DesktopModeFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue() &&
                DesktopModeStatus.canEnterDesktopMode(mContext)
                && mDesktopUserRepositories.isPresent()) {
            DesktopRepository repository =
                    mDesktopUserRepositories.get().getProfile(taskInfo.userId);
            // TODO: b/370038902 - Handle Activity#finishAndRemoveTask.
            if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()
                    || !repository.isMinimizedTask(taskInfo.taskId)) {
                // A task that's vanishing should be removed:
                // - If it's not yet minimized. It can be minimized when a back navigation is
                // triggered on a task and the task is closing. It will be marked as minimized in
                // [DesktopTasksTransitionObserver] before it gets here.
                repository.removeClosingTask(taskInfo.taskId);
                repository.removeTask(taskInfo.displayId, taskInfo.taskId);
            }
        }
        // TODO: b/367268649 - This listener shouldn't need to call the transition observer directly
        // for logging once the logic in the observer is moved.
        mDesktopModeLoggerTransitionObserver.onTaskVanished(taskInfo);
        mWindowDecorationViewModel.onTaskVanished(taskInfo);
        updateLaunchAdjacentController();
    }

    @Override
    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
        final State state = mTasks.get(taskInfo.taskId);

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Freeform Task Info Changed: #%d",
                taskInfo.taskId);
        mDesktopTasksController.ifPresent(c -> c.onTaskInfoChanged(taskInfo));
        mWindowDecorationViewModel.onTaskInfoChanged(taskInfo);
        state.mTaskInfo = taskInfo;
        if (DesktopModeStatus.canEnterDesktopMode(mContext)) {
            if (DesktopModeFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue()) {
                // Pass task info changes to the [TaskChangeListener] since [TransitionsObserver]
                // does not propagate all task info changes.
                mTaskChangeListener.ifPresent(listener ->
                        listener.onNonTransitionTaskChanging(taskInfo));
            } else if (mDesktopUserRepositories.isPresent()) {
                DesktopRepository currentRepo =
                        mDesktopUserRepositories.get().getProfile(taskInfo.userId);
                currentRepo.updateTask(taskInfo.displayId, taskInfo.taskId,
                        taskInfo.isVisible);
            }
        }
        updateLaunchAdjacentController();
        onTaskEnteredFreeform(taskInfo);
    }

    private void updateLaunchAdjacentController() {
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue()) {
            // With multiple desks, freeform tasks are children of a root task controlled by
            // DesksOrganizer, so toggling launch-adjacent should be managed there.
            return;
        }
        for (int i = 0; i < mTasks.size(); i++) {
            if (mTasks.valueAt(i).mTaskInfo.isVisible) {
                mLaunchAdjacentController.setLaunchAdjacentEnabled(false);
                return;
            }
        }
        mLaunchAdjacentController.setLaunchAdjacentEnabled(true);
    }

    void onTaskEnteredFreeform(RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            Log.d(LOG_TAG, "Task entered freeform: taskInfo is null");
            return;
        }

        int windowingMode = taskInfo.getWindowingMode();
        Log.d(LOG_TAG, String.format("Task #%d windowingMode: %d (FREEFORM=%d)",
                taskInfo.taskId, windowingMode, WINDOWING_MODE_FREEFORM));

        if (windowingMode == WINDOWING_MODE_FREEFORM) {
            // Calculate DPI based on window size relative to screen size
            mMainHandler.postDelayed(() -> {
                // Get the bounds of the task
                Rect bounds = taskInfo.configuration.windowConfiguration.getBounds();
                int windowWidth = bounds.width();
                int windowHeight = bounds.height();

                // Get display metrics to determine screen size
                DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
                int screenWidth = displayMetrics.widthPixels;
                int screenHeight = displayMetrics.heightPixels;
                int systemDpi = displayMetrics.densityDpi;

                // Calculate the area ratio
                float widthRatio = (float)windowWidth / screenWidth;
                float heightRatio = (float)windowHeight / screenHeight;

                // Calculate area ratio and then take the square root for better scaling
                float areaRatio = widthRatio * heightRatio;
                float scaleFactor = (float)Math.sqrt(areaRatio);
                int calculatedDpi = Math.round(systemDpi * scaleFactor);

                // Log all values for debugging
                Log.d(LOG_TAG, String.format(
                    "Window: %dx%d, Screen: %dx%d, System DPI: %d, Width ratio: %.2f, Height ratio: %.2f",
                    windowWidth, windowHeight, screenWidth, screenHeight, systemDpi, widthRatio, heightRatio));
                Log.d(LOG_TAG, String.format(
                    "Area ratio: %.4f, Square root (scale factor): %.4f, Calculated DPI: %d",
                    areaRatio, scaleFactor, calculatedDpi));

                final WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.setDensityDpi(taskInfo.token, calculatedDpi);
                mShellTaskOrganizer.applyTransaction(wct);
            }, 16);
        } else {
            Log.d(LOG_TAG, String.format("Task #%d is not in freeform mode: %d, not changing DPI",
                    taskInfo.taskId, windowingMode));
        }
    }

    @Override
    public void onFocusTaskChanged(RunningTaskInfo taskInfo) {
        if (taskInfo.getWindowingMode() != WINDOWING_MODE_FREEFORM
                || DesktopModeFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue()) {
            return;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG,
                "Freeform Task Focus Changed: #%d focused=%b",
                taskInfo.taskId, taskInfo.isFocused);
        if (DesktopModeStatus.canEnterDesktopMode(mContext) && taskInfo.isFocused
                && mDesktopUserRepositories.isPresent()) {
            DesktopRepository repository =
                mDesktopUserRepositories.get().getProfile(taskInfo.userId);
            repository.addTask(taskInfo.displayId, taskInfo.taskId, taskInfo.isVisible);
        }
        onTaskEnteredFreeform(taskInfo);
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        b.setParent(findTaskSurface(taskId));
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        t.reparent(sc, findTaskSurface(taskId));
    }

    private SurfaceControl findTaskSurface(int taskId) {
        if (!mTasks.contains(taskId)) {
            throw new IllegalArgumentException("There is no surface for taskId=" + taskId);
        }
        return mTasks.get(taskId).mLeash;
    }

    @Override
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + this);
        pw.println(innerPrefix + mTasks.size() + " tasks");
    }

    @Override
    public String toString() {
        return TAG;
    }

    private static class State {
        RunningTaskInfo mTaskInfo;
        SurfaceControl mLeash;
    }
}
