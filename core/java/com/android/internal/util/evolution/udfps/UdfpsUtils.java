/*
* Copyright (C) 2025 VoltageOS
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android.internal.util.evolution.udfps;

import android.content.Context;
import android.content.pm.PackageManager;
import com.android.internal.util.evolution.Utils;

public class UdfpsUtils {
    private static final String UDFPS_ANIMATIONS_PACKAGE = "org.evolution.udfps.animations";
    public static boolean hasUdfpsSupport(Context context) {
        return Utils.isPackageInstalled(context, UDFPS_ANIMATIONS_PACKAGE);
    }
}
