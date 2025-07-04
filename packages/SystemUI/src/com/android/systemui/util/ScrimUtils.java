/*
 * Copyright (C) 2025 The AxionAOSP Project
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
package com.android.systemui.util;

import static com.android.systemui.statusbar.StatusBarState.KEYGUARD;
import static com.android.systemui.statusbar.StatusBarState.SHADE_LOCKED;

import com.android.systemui.Dependency;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import java.util.function.Consumer;

public class ScrimUtils {

    public interface ScrimEventListener {
        default void onKeyguardShowingChanged(boolean showing) {}
        default void onKeyguardFadingAwayChanged(boolean fadingAway) {}
        default void onKeyguardGoingAwayChanged(boolean goingAway) {}
        default void onPrimaryBouncerShowingChanged(boolean showing) {}
        default void onDozingChanged() {}
        default void onExpandedFractionChanged(float expandedFraction) {}
        default void onBarStateChanged(int state) {}
        default void onQsVisibilityChanged(boolean visible) {}
    }

    private static ScrimUtils instance;

    private StatusBarStateController mStatusBarStateController;
    private KeyguardStateController mKeyguardStateController;

    private final WeakListenerManager<ScrimEventListener> listeners = new WeakListenerManager<>();

    private boolean mIsDozing = false;
    private boolean mQsVisible = false;
    private float mExpandedFraction = 0f;
    private int mBarState = -1;

    private final KeyguardStateController.Callback mKeyguardStateCallback =
            new KeyguardStateController.Callback() {
                @Override
                public void onKeyguardFadingAwayChanged() {
                    notifyKeyguardFadingAwayChanged(mKeyguardStateController.isKeyguardFadingAway());
                }

                @Override
                public void onKeyguardGoingAwayChanged() {
                    notifyKeyguardGoingAwayChanged(mKeyguardStateController.isKeyguardGoingAway());
                }

                @Override
                public void onPrimaryBouncerShowingChanged() {
                    notifyPrimaryBouncerShowingChanged(mKeyguardStateController.isPrimaryBouncerShowing());
                }
            };

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
                @Override
                public void onStateChanged(int newState) {
                    setBarState(newState);
                }

                @Override
                public void onDozingChanged(boolean dozing) {
                    if (mIsDozing != dozing) {
                        mIsDozing = dozing;
                        notifyDozingChanged();
                    }
                }
            };

    private ScrimUtils() {}

    public static ScrimUtils get() {
        if (instance == null) {
            instance = new ScrimUtils();
        }
        return instance;
    }

    public void init(KeyguardStateController keyguardStateController) {
        mKeyguardStateController = keyguardStateController;
        mKeyguardStateController.addCallback(mKeyguardStateCallback);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
    }

    public void addListener(ScrimEventListener listener) {
        listeners.addListener(listener);
    }

    public void removeListener(ScrimEventListener listener) {
        listeners.removeListener(listener);
    }

    private void notifyListeners(Consumer<ScrimEventListener> callback) {
        listeners.notifyConsumer(callback);
    }

    private void notifyKeyguardShowingChanged(boolean showing) {
        notifyListeners(listener -> listener.onKeyguardShowingChanged(showing));
    }

    private void notifyKeyguardGoingAwayChanged(boolean goingAway) {
        notifyListeners(listener -> listener.onKeyguardGoingAwayChanged(goingAway));
    }

    private void notifyKeyguardFadingAwayChanged(boolean fadingAway) {
        notifyListeners(listener -> listener.onKeyguardFadingAwayChanged(fadingAway));
    }

    private void notifyPrimaryBouncerShowingChanged(boolean showing) {
        notifyListeners(listener -> listener.onPrimaryBouncerShowingChanged(showing));
    }

    private void notifyDozingChanged() {
        notifyListeners(ScrimEventListener::onDozingChanged);
    }

    private void notifyExpandedFractionChanged(float fraction) {
        notifyListeners(listener -> listener.onExpandedFractionChanged(fraction));
    }

    private void notifyBarStateChanged(int state) {
        notifyListeners(listener -> listener.onBarStateChanged(state));
    }

    private void notifyQsVisibilityChanged(boolean visible) {
        notifyListeners(listener -> listener.onQsVisibilityChanged(visible));
    }
    
    public void setKeyguardShowing(boolean showing) {
        notifyKeyguardShowingChanged(showing);
    }

    public void setExpandedFraction(float expandedFraction) {
        if (expandedFraction == 0.0f || expandedFraction == 1.0f) {
            if (mExpandedFraction != expandedFraction) {
                mExpandedFraction = expandedFraction;
                notifyExpandedFractionChanged(expandedFraction);
            }
        }
    }

    public void setBarState(int state) {
        if (mBarState != state) {
            mBarState = state;
            notifyBarStateChanged(state);
        }
    }

    public void setQsVisible(boolean visible) {
        if (mQsVisible != visible) {
            mQsVisible = visible;
            notifyQsVisibilityChanged(visible);
        }
    }

    public boolean isDozing() {
        return mIsDozing;
    }

    public boolean isKeyguardShowing() {
        return mBarState == KEYGUARD;
    }

    public boolean isPanelFullyCollapsed() {
        int state = mBarState;
        if (state == SHADE_LOCKED || state == KEYGUARD) {
            return !mQsVisible;
        }
        return mExpandedFraction <= 0.0f;
    }
}
