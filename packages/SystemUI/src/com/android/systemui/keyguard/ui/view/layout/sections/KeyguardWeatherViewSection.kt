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
import com.android.systemui.keyguard.MigrateClocksToBlueprint
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
        if (!MigrateClocksToBlueprint.isEnabled) return

        // Look for existing weather container or create views
        val weatherContainer = constraintLayout.findViewById<ViewGroup?>(R.id.keyguard_weather)
        
        if (weatherContainer != null) {
            // If container exists, find the weather views within it
            weatherImageView = weatherContainer.findViewById(R.id.default_weather_image)
            weatherTextView = weatherContainer.findViewById(R.id.default_weather_text)
            
            // Remove container from parent and add to constraint layout
            (weatherContainer.parent as? ViewGroup)?.removeView(weatherContainer)
            constraintLayout.addView(weatherContainer)
        } else {
            // Create weather views directly if no container exists
            createWeatherViews(constraintLayout)
        }

        // Initialize weather views
        initializeWeatherViews()
    }

    private fun createWeatherViews(constraintLayout: ConstraintLayout) {
        // Create weather image view
        weatherImageView = WeatherImageView(context).apply {
            id = R.id.default_weather_image
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }

        // Create weather text view
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

        // Add views to constraint layout
        weatherImageView?.let { constraintLayout.addView(it) }
        weatherTextView?.let { constraintLayout.addView(it) }
    }

    private fun initializeWeatherViews() {
        // Weather views will initialize their controllers automatically
        // when attached to window through their onAttachedToWindow() methods
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        // Weather data binding is handled by individual weather views
        // through their WeatherViewController instances
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled) return

        constraintSet.apply {
            val startMargin = context.resources.getDimensionPixelSize(custR.dimen.clock_padding_start) +
                context.resources.getDimensionPixelSize(custR.dimen.status_view_margin_horizontal)

            // Handle weather container if it exists
            if (constraintSet.getConstraint(R.id.keyguard_weather) != null) {
                connect(
                    R.id.keyguard_weather,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    startMargin
                )
                connect(
                    R.id.keyguard_weather,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )
                constrainHeight(R.id.keyguard_weather, ConstraintSet.WRAP_CONTENT)
                connect(
                    R.id.keyguard_weather,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM
                )

                createBarrier(
                    R.id.smart_space_barrier_bottom,
                    Barrier.BOTTOM,
                    0,
                    *intArrayOf(R.id.keyguard_weather)
                )
            } else {
                // Apply constraints to individual weather views
                applyWeatherImageConstraints(constraintSet, startMargin)
                applyWeatherTextConstraints(constraintSet)
                
                // Create barrier for both weather views
                createBarrier(
                    R.id.smart_space_barrier_bottom,
                    Barrier.BOTTOM,
                    0,
                    *intArrayOf(R.id.default_weather_image, R.id.default_weather_text)
                )
            }
        }
    }

    private fun applyWeatherImageConstraints(constraintSet: ConstraintSet, startMargin: Int) {
        if (constraintSet.getConstraint(R.id.default_weather_image) != null) {
            constraintSet.apply {
                connect(
                    R.id.default_weather_image,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    startMargin
                )
                connect(
                    R.id.default_weather_image,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM
                )
                constrainHeight(R.id.default_weather_image, ConstraintSet.WRAP_CONTENT)
                constrainWidth(R.id.default_weather_image, ConstraintSet.WRAP_CONTENT)
            }
        }
    }

    private fun applyWeatherTextConstraints(constraintSet: ConstraintSet) {
        if (constraintSet.getConstraint(R.id.default_weather_text) != null) {
            constraintSet.apply {
                connect(
                    R.id.default_weather_text,
                    ConstraintSet.START,
                    R.id.default_weather_image,
                    ConstraintSet.END,
                    context.resources.getDimensionPixelSize(R.dimen.weather_text_margin_start)
                )
                connect(
                    R.id.default_weather_text,
                    ConstraintSet.TOP,
                    R.id.default_weather_image,
                    ConstraintSet.TOP
                )
                connect(
                    R.id.default_weather_text,
                    ConstraintSet.BOTTOM,
                    R.id.default_weather_image,
                    ConstraintSet.BOTTOM
                )
                connect(
                    R.id.default_weather_text,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )
                constrainHeight(R.id.default_weather_text, ConstraintSet.WRAP_CONTENT)
                constrainWidth(R.id.default_weather_text, ConstraintSet.WRAP_CONTENT)
            }
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        // Weather views will clean themselves up through their
        // onDetachedFromWindow() methods which call disableUpdates() and removeObserver()
        
        // Find and remove weather container if it exists
        constraintLayout.findViewById<ViewGroup?>(R.id.keyguard_weather)?.let { weatherContainer ->
            constraintLayout.removeView(weatherContainer)
        }
        
        // Remove individual weather views if they were created directly
        weatherImageView?.let { constraintLayout.removeView(it) }
        weatherTextView?.let { constraintLayout.removeView(it) }
        
        // Clear references
        weatherImageView = null
        weatherTextView = null
    }
}
