// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif;

public class AppConstants {

    // Keys used to pass data between activities and to store data in SharedPreference.
    public static final String KEY_WIFI_SECURITY_TYPE = "wifi_security";
    public static final String KEY_PROOF_OF_POSSESSION = "proof_of_possession";
    public static final String KEY_WIFI_DEVICE_NAME_PREFIX = "wifi_network_name_prefix";
    public static final String KEY_BLE_DEVICE_NAME_PREFIX = "ble_device_name_prefix";
    public static final String KEY_DEVICE_NAME = "device_name";
    public static final String KEY_STATUS_MSG = "status_msg";

    public static final String PREFERENCES_DEVICE_TRANSPORT_KEY = "device_types";
    public static final String PREFERENCES_SECURITY_TYPE_KEY = "security_type";
    public static final String KEY_USER_NAME = "sec2_username";
    // Configuration option keys used to pass data in intents.
    public static final String KEY_WIFI_CREDENTIALS = "wifi_creds";
    public static final String KEY_DEVICE_CONFIGURATION = "dev_config";
    public static final String KEY_WIFI_SSID = "ssid";
    public static final String KEY_WIFI_PASSWORD = "password";
    public static final String KEY_OPENWEATHER_API_KEY = "openweather_api_key";
    public static final String KEY_LOCATION_LAITUDE = "latitude";
    public static final String KEY_LOCATION_LONGITUDE = "longitude";
    public static final String KEY_LOCATION_ZIPCODE = "latitude";
    public static final String KEY_LOCATION_COUNTRY_CODE = "latitude";
    public static final String KEY_LOCATION_LANGUAGE = "latitude";
    public static final String KEY_LOCATION_UNIT_SYSTEM = "latitude";

    public static final String ESP_PREFERENCES = "Esp_Preferences";

    public static final String DEVICE_TYPE_SOFTAP = "softap";
    public static final String DEVICE_TYPE_BLE = "ble";
    public static final String DEVICE_TYPE_BOTH = "both";
    public static final String DEVICE_TYPE_DEFAULT = DEVICE_TYPE_BOTH;

    public static final int SEC_TYPE_0 = 0;
    public static final int SEC_TYPE_1 = 1;
    public static final int SEC_TYPE_2 = 2;
    public static final int SEC_TYPE_DEFAULT = SEC_TYPE_2;
    public static final String DEFAULT_USER_NAME = "wifiprov";

    public static final String CUSTOM_CONFIG_ENDPOINT = "config";
}
