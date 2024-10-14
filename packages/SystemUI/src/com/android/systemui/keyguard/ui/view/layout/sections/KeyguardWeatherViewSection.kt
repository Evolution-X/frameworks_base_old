/*
 * Copyright (C) 2025 the RisingOS Revived Android Project
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
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.customization.R as custR
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import com.android.systemui.weather.WeatherImageView
import com.android.systemui.weather.WeatherTextView
import javax.inject.Inject

class KeyguardWeatherViewSection @Inject constructor(
    private val context: Context,
) : KeyguardSection() {

    private var weatherImageView: WeatherImageView? = null
    private var weatherTextView: WeatherTextView? = null

    override fun addViews(constraintLayout: ConstraintLayout) {

        val weatherContainer = constraintLayout.findViewById<ViewGroup?>(R.id.keyguard_weather)
        
        if (weatherContainer != null) {
            weatherImageView = weatherContainer.findViewById(R.id.default_weather_image)
            weatherTextView = weatherContainer.findViewById(R.id.default_weather_text)
            
            (weatherContainer.parent as? ViewGroup)?.removeView(weatherContainer)
            constraintLayout.addView(weatherContainer)
        } else {
            createWeatherViews(constraintLayout)
        }

        initializeWeatherViews()
    }

    private fun createWeatherViews(constraintLayout: ConstraintLayout) {
        weatherImageView = WeatherImageView(context).apply {
            id = R.id.default_weather_image
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }

        weatherTextView = WeatherTextView(context).apply {
            id = R.id.default_weather_text
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(context.getColor(android.R.color.white))
            textSize = 20f
            visibility = View.GONE
        }

        weatherImageView?.let { constraintLayout.addView(it) }
        weatherTextView?.let { constraintLayout.addView(it) }
    }

    private fun initializeWeatherViews() {
        // Weather views initialize automatically when attached to window
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        // Weather data binding handled by individual weather views
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {

        constraintSet.apply {
            val startMargin = context.resources.getDimensionPixelSize(custR.dimen.clock_padding_start) +
                context.resources.getDimensionPixelSize(custR.dimen.status_view_margin_horizontal)

            // Weather positioning - below CLOCK (2nd in hierarchy)
            if (constraintSet.getConstraint(R.id.keyguard_weather) != null) {
                connect(R.id.keyguard_weather, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, startMargin)
                connect(R.id.keyguard_weather, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                
                // Chain to clock (primary) or fallback to slice_view
                if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                    connect(R.id.keyguard_weather, ConstraintSet.TOP, R.id.clock_ls, ConstraintSet.BOTTOM, 8)
                } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                    connect(R.id.keyguard_weather, ConstraintSet.TOP, R.id.keyguard_slice_view, ConstraintSet.BOTTOM, 8)
                } else {
                    connect(R.id.keyguard_weather, ConstraintSet.TOP, R.id.lockscreen_clock_view, ConstraintSet.BOTTOM, 8)
                }
                
                constrainHeight(R.id.keyguard_weather, ConstraintSet.WRAP_CONTENT)
                constrainWidth(R.id.keyguard_weather, ConstraintSet.MATCH_CONSTRAINT)
            } else {
                applyWeatherImageConstraints(constraintSet, startMargin)
                applyWeatherTextConstraints(constraintSet)
            }
            
            // UNIFIED BARRIER - Create barrier in every section that could be last
            createUnifiedBarrierAndNotificationConstraints(constraintSet)
        }
    }

    private fun applyWeatherImageConstraints(constraintSet: ConstraintSet, startMargin: Int) {
        if (constraintSet.getConstraint(R.id.default_weather_image) != null) {
            constraintSet.apply {
                connect(R.id.default_weather_image, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, startMargin)
                
                if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                    connect(R.id.default_weather_image, ConstraintSet.TOP, R.id.clock_ls, ConstraintSet.BOTTOM, 8)
                } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                    connect(R.id.default_weather_image, ConstraintSet.TOP, R.id.keyguard_slice_view, ConstraintSet.BOTTOM, 8)
                } else {
                    connect(R.id.default_weather_image, ConstraintSet.TOP, R.id.lockscreen_clock_view, ConstraintSet.BOTTOM, 8)
                }
                
                constrainHeight(R.id.default_weather_image, ConstraintSet.WRAP_CONTENT)
                constrainWidth(R.id.default_weather_image, ConstraintSet.WRAP_CONTENT)
            }
        }
    }

    private fun applyWeatherTextConstraints(constraintSet: ConstraintSet) {
        if (constraintSet.getConstraint(R.id.default_weather_text) != null) {
            constraintSet.apply {
                connect(R.id.default_weather_text, ConstraintSet.START, R.id.default_weather_image, ConstraintSet.END,
                    context.resources.getDimensionPixelSize(R.dimen.weather_text_margin_start))
                connect(R.id.default_weather_text, ConstraintSet.TOP, R.id.default_weather_image, ConstraintSet.TOP)
                connect(R.id.default_weather_text, ConstraintSet.BOTTOM, R.id.default_weather_image, ConstraintSet.BOTTOM)
                connect(R.id.default_weather_text, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                constrainHeight(R.id.default_weather_text, ConstraintSet.WRAP_CONTENT)
                constrainWidth(R.id.default_weather_text, ConstraintSet.WRAP_CONTENT)
            }
        }
    }
    
    private fun createUnifiedBarrierAndNotificationConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            // UNIFIED BARRIER - Include ALL status area elements
            createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *intArrayOf(
                    R.id.keyguard_slice_view,
                    R.id.keyguard_weather,
                    R.id.default_weather_image,
                    R.id.default_weather_text,
                    R.id.clock_ls,
                    R.id.lockscreen_clock_view // Include fallback clock
                )
            )
            
            // Position notifications below ALL status area content
            if (constraintSet.getConstraint(R.id.left_aligned_notification_icon_container) != null) {
                connect(
                    R.id.left_aligned_notification_icon_container,
                    ConstraintSet.TOP,
                    R.id.smart_space_barrier_bottom,
                    ConstraintSet.BOTTOM,
                    context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
                )
            }
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        constraintLayout.findViewById<ViewGroup?>(R.id.keyguard_weather)?.let { weatherContainer ->
            constraintLayout.removeView(weatherContainer)
        }
        
        weatherImageView?.let { constraintLayout.removeView(it) }
        weatherTextView?.let { constraintLayout.removeView(it) }
        
        weatherImageView = null
        weatherTextView = null
    }
}
