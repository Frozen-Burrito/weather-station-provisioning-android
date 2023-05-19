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

package com.espressif.ui.activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.espressif.AppConstants;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.ui.models.DeviceTransportType;
import com.espressif.wifi_provisioning.BuildConfig;
import com.espressif.wifi_provisioning.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 2;

    private ESPProvisionManager provisioningManager;
    private DeviceTransportType deviceTransport;
    private static SharedPreferences sharedPreferences;

    private ImageView mainBackgroundImage;

    private final ActivityResultLauncher<Intent> startForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResultCallback<ActivityResult>) this::handleActivityResult
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        initViews();

        provisioningManager = ESPProvisionManager.getInstance(getApplicationContext());

        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
    }

    private void initViews() {
        mainBackgroundImage = findViewById(R.id.main_background_img);

        FloatingActionButton provisionNewDeviceBtn = findViewById(R.id.start_provisioning_btn);
        provisionNewDeviceBtn.setOnClickListener(addDeviceBtnClickListener);

        try {
            TextView appVersionTextView = findViewById(R.id.app_version_text);

            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            String appVersionLabel = String.format("%s - v%s", getString(R.string.app_version), version);
            appVersionTextView.setText(appVersionLabel);

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        deviceTransport = getProvisioningTransportFromPrefs();

        switch (deviceTransport) {
            case BOTH:
                mainBackgroundImage.setImageResource(R.drawable.ic_esp);
                break;
            case SOFTAP:
                mainBackgroundImage.setImageResource(R.drawable.ic_esp_softap);
                break;
            case BLE:
                mainBackgroundImage.setImageResource(R.drawable.ic_esp_ble);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (BuildConfig.isSettingsAllowed) {
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.menu_settings, menu);
        } else {
            menu.clear();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (R.id.action_settings == id) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private void handleActivityResult(ActivityResult result) {
        switch (result.getResultCode()) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (isLocationEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    onProvisionNewDeviceClick();
                }
                break;
            case BLUETOOTH_PERMISSION_REQUEST_CODE:
                Toast.makeText(this, getString(R.string.bt_permission_granted_msg), Toast.LENGTH_LONG)
                        .show();
                break;
        }
    }

    View.OnClickListener addDeviceBtnClickListener = v -> {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !isLocationEnabled()) {
            askForLocation();
        } else {
            onProvisionNewDeviceClick();
        }
    };

    private void startProvisioningFlow() {
        deviceTransport = getProvisioningTransportFromPrefs();

        // If the device supports both transports, ask the user to choose one. After this,
        // deviceTransport is either BLE or SOFTAP.
        if (DeviceTransportType.BOTH == deviceTransport) {
            askForPreferredTransport();
        }

        final ESPConstants.TransportType transportType = (DeviceTransportType.BLE == deviceTransport)
                ? ESPConstants.TransportType.TRANSPORT_BLE
                : ESPConstants.TransportType.TRANSPORT_SOFTAP;

        final ESPConstants.SecurityType securityType = getProvisioningSecurityFromPrefs();

        Log.d(TAG, "Provisioning for transport: " + deviceTransport.name());
        Log.d(TAG, "Security level: " + securityType.name());

        provisioningManager.createESPDevice(transportType, securityType);

        navigateToProvisioningActivity(securityType);
    }

    private void askForLocation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_msg_gps);

        builder.setPositiveButton(R.string.btn_ok, (dialog, which) -> startForResult.launch(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));

        builder.setNegativeButton(R.string.btn_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void onProvisionNewDeviceClick() {

        if (BuildConfig.isQrCodeSupported) {

            navigateToQrActivity();

        } else if (doesDeviceSupportBleTransport(deviceTransport)) {
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bleAdapter = bluetoothManager.getAdapter();

            if (!bleAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startForResult.launch(enableBtIntent);
            } else {
                startProvisioningFlow();
            }
        } else {
            startProvisioningFlow();
        }
    }

    private void askForPreferredTransport() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(R.string.dialog_msg_device_selection);

        final String[] availableTransports = {"BLE", "SoftAP"};

        builder.setItems(availableTransports, (dialog, position) -> {
            switch (position) {
                case 0:
                    deviceTransport = DeviceTransportType.BLE;
                    break;
                case 1:
                    deviceTransport = DeviceTransportType.SOFTAP;
                    break;
            }
            dialog.dismiss();
        });
        builder.show();
    }

    private boolean isLocationEnabled() {
        boolean isGpsEnabled = false;
        boolean isNetworkEnabled = false;
        LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Activity.LOCATION_SERVICE);

        try {
            isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            Log.e(TAG, "An exception ocurred while checking for GPS: " + ex.getMessage());
        }

        try {
            isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            Log.e(TAG, "An exception ocurred while checking for network: " + ex.getMessage());
        }

        return (isGpsEnabled || isNetworkEnabled);
    }

    private boolean doesDeviceSupportBleTransport(DeviceTransportType transport) {
        return (DeviceTransportType.BLE == transport || DeviceTransportType.BOTH == transport);
    }

    private void navigateToQrActivity() {
        Intent intent = new Intent(MainActivity.this, AddDeviceActivity.class);
        startActivity(intent);
    }

    private void navigateToProvisioningActivity(ESPConstants.SecurityType securityType) {
        Intent intent;

        if (DeviceTransportType.BLE == deviceTransport) {
            intent = new Intent(MainActivity.this, BLEProvisionLanding.class);
        } else {
            intent = new Intent(MainActivity.this, ProvisionLanding.class);

        }
        intent.putExtra(AppConstants.PREFERENCES_SECURITY_TYPE, securityType.name());

        startActivity(intent);
    }

    private DeviceTransportType getProvisioningTransportFromPrefs() {
        final int transportTypeOrdinal = sharedPreferences.getInt(
                AppConstants.PREFERENCES_DEVICE_TRANSPORT_KEY,
                DeviceTransportType.BOTH.ordinal()
        );

        return DeviceTransportType.values()[transportTypeOrdinal];
    }

    public ESPConstants.SecurityType getProvisioningSecurityFromPrefs() {
        final int securityTypeOrdinal = sharedPreferences.getInt(
                AppConstants.PREFERENCES_SECURITY_TYPE,
                ESPConstants.SecurityType.SECURITY_0.ordinal()
        );

        return ESPConstants.SecurityType.values()[securityTypeOrdinal];
    }

}
