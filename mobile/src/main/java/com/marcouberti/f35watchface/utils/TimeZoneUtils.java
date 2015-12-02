package com.marcouberti.f35watchface.utils;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by Marco on 30/11/15.
 */
public class TimeZoneUtils {
    public static List<String> getAllTimezones() {
        List<String> list = Arrays.asList(TimeZone.getAvailableIDs());
        return list;
    }
}
