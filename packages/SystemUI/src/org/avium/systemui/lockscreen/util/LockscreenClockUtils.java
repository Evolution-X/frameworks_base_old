package org.avium.systemui.lockscreen.util;

import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.util.Log;
import java.util.Date;
import java.util.Locale;

public class LockscreenClockUtils {

    private static final String TAG = "AVIUM_LOCKSCREEN";
    
    private static final int DEFAULT_COLOR = Color.WHITE;

    public static String getCurrentTimeString(String pattern, Locale locale) {
        try {
            Date now = new Date();
            SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
            String timeString = format.format(now);
            Log.v(TAG, "getCurrentTimeString: pattern=" + pattern + ", result=" + timeString);
            return timeString;
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time with pattern: " + pattern, e);
            return "";
        }
    }

    public static String getCurrentTimeString(String pattern) {
        return getCurrentTimeString(pattern, Locale.getDefault());
    }

    public static String getCurrentDateString() {
        return getCurrentTimeString("M月d日 EEEE", Locale.CHINESE);
    }

    public static int parseColor(String colorString) {
        if (colorString == null || colorString.trim().isEmpty()) {
            return DEFAULT_COLOR;
        }

        String color = colorString.trim().toLowerCase();

        try {
            if (color.startsWith("#")) {
                int parsed = Color.parseColor(color);
                return parsed;
            } else if (color.matches("^[0-9a-f]{6}$") || color.matches("^[0-9a-f]{8}$")) {
                String hexColor = "#" + color;
                int parsed = Color.parseColor(hexColor);
                return parsed;
            }

            switch (color) {
                case "red":
                    return Color.RED;
                case "green":
                    return Color.GREEN;
                case "blue":
                    return Color.BLUE;
                case "white":
                    return Color.WHITE;
                case "black":
                    return Color.BLACK;
                case "yellow":
                    return Color.YELLOW;
                case "cyan":
                    return Color.CYAN;
                case "magenta":
                    return Color.MAGENTA;
                case "gray":
                case "grey":
                    return Color.GRAY;
                default:
                    Log.w(TAG, "parseColor: unknown color name '" + color + "', using default white");
                    return DEFAULT_COLOR;
            }
        } catch (Exception e) {
            return DEFAULT_COLOR;
        }
    }

    public static boolean isValidColor(String colorString) {
        if (colorString == null || colorString.trim().isEmpty()) {
            return false;
        }

        String color = colorString.trim().toLowerCase();
        
        try {
            if (color.startsWith("#")) {
                Color.parseColor(color);
                return true;
            } else if (color.matches("^[0-9a-f]{6}$") || color.matches("^[0-9a-f]{8}$")) {
                Color.parseColor("#" + color);
                return true;
            }
            switch (color) {
                case "red":
                case "green":
                case "blue":
                case "white":
                case "black":
                case "yellow":
                case "cyan":
                case "magenta":
                case "gray":
                case "grey":
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static char getTimeDigitAt(String timeString, int position) {
        if (timeString == null || position < 0 || position >= timeString.length()) {
            return '0';
        }
        return timeString.charAt(position);
    }

    public static int charDigitToInt(char digitChar) {
        if (digitChar >= '0' && digitChar <= '9') {
            return digitChar - '0';
        }
        return 0;
    }

    public static String getLunarDateString() {
        final String[] LUNAR_YEAR_NAMES = {"甲子", "乙丑", "丙寅", "丁卯", "戊辰", "己巳", "庚午", "辛未", "壬申", "癸酉",
                                          "甲戌", "乙亥", "丙子", "丁丑", "戊寅", "己卯", "庚辰", "辛巳", "壬午", "癸未",
                                          "甲申", "乙酉", "丙戌", "丁亥", "戊子", "己丑", "庚寅", "辛卯", "壬辰", "癸巳",
                                          "甲午", "乙未", "丙申", "丁酉", "戊戌", "己亥", "庚子", "辛丑", "壬寅", "癸卯",
                                          "甲辰", "乙巳", "丙午", "丁未", "戊申", "己酉", "庚戌", "辛亥", "壬子", "癸丑"};
        final String[] LUNAR_MONTH_NAMES = {"正月", "二月", "三月", "四月", "五月", "六月",
                                            "七月", "八月", "九月", "十月", "冬月", "腊月"};
        final String[] LUNAR_DAY_NAMES = {"初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
                                          "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
                                          "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"};

        try {
            android.icu.util.ChineseCalendar calendar = new android.icu.util.ChineseCalendar();
            int yearCycle = calendar.get(android.icu.util.ChineseCalendar.EXTENDED_YEAR) - 2637;
            int month = calendar.get(android.icu.util.ChineseCalendar.MONTH);
            int day = calendar.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH);

            String yearStr = (yearCycle > 0 && yearCycle <= 60) ? LUNAR_YEAR_NAMES[yearCycle - 1] + "年" : "";
            String monthStr = LUNAR_MONTH_NAMES[month];
            String dayStr = LUNAR_DAY_NAMES[day - 1];

            return yearStr + " " + monthStr + dayStr;
        } catch (Exception e) {
            return "";
        }
    }
}