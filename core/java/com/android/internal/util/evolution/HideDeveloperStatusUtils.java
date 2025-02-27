package com.android.internal.util.evolution;

import android.content.ContentResolver;
import android.provider.Settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class HideDeveloperStatusUtils {

    private static final Set<String> SETTINGS_TO_HIDE = Collections.unmodifiableSet(
        new HashSet<>(
            Arrays.asList(
                "adb_enabled",
                "adb_wifi_enabled",
                "development_settings_enabled"
            )
        )
    );

    public static boolean shouldHideDevStatus(ContentResolver cr, String packageName, String name) {
        if (cr == null || packageName == null || name == null || !SETTINGS_TO_HIDE.contains(name)) {
            return false;
        }
        Set<String> apps = getApps(cr);
        return !apps.isEmpty() && apps.contains(packageName);
    }

    private static Set<String> getApps(ContentResolver cr) {
        if (cr == null) {
            return Collections.emptySet();
        }
        String apps = Settings.Secure.getString(cr, "hide_developer_status");
        if (apps == null || apps.isEmpty() || apps.equals(",")) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(apps.split(","))));
    }
}
