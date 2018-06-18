package com.empatica.sample;

import android.Manifest;

public class Constants {

    public static final int REQUEST_ENABLE_BT = 1;

    public static final int REQUEST_CODE_LOCATION_NETWORK_BT = 2;

    public static final String[] REQUESTED_PERMISSIONS = new String[] {
            // Android 6 (API level 23) now requires ACCESS_COARSE_LOCATION permission to use BLE
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE
    };

    public static final String EMPATICA_API_KEY = "f07c477c52fe4c86a9f0dabfa2a8bf0c";

}