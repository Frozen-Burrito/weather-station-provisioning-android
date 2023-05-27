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

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.espressif.AppConstants;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.listeners.QRCodeScanListener;
import com.espressif.ui.models.DeviceTransportType;
import com.espressif.ui.utils.Utils;
import com.espressif.wifi_provisioning.R;
import com.google.android.material.card.MaterialCardView;
import com.wang.avi.AVLoadingIndicatorView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScanQrActivity extends AppCompatActivity {

    private static final String TAG = ScanQrActivity.class.getSimpleName();

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final int ACCESS_FINE_LOCATION_REQUEST_CODE = 2;
    private static final int ENABLE_BT_REQUEST_CODE = 3;

    public static final String PROV_DATA_SECURITY_VERSION_FIELD = "sec_ver";
    public static final String DEVICE_CAPABILITIES_WIFI_SCAN = "wifi_scan";

    private SharedPreferences sharedPreferences;

    private ESPDevice espDevice;
    private ESPProvisionManager provisioningManager;

    private CodeScanner codeScanner;
    private LinearLayout layoutQrCode, layoutPermissionErr;
    private TextView permissionErrorText;
    private ImageView permissionErrorImage;
    private AVLoadingIndicatorView loadingIndicator;

    private boolean isQrCodeDataReceived = false;

    private final ActivityResultLauncher<Intent> startForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResultCallback<ActivityResult>) this::handleActivityResult
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scan_qr_code);

        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        provisioningManager = ESPProvisionManager.getInstance(getApplicationContext());

        EventBus.getDefault().register(this);

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();

        final boolean areCameraAndLocationGranted = isPermissionGranted(Manifest.permission.CAMERA)
                && isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION);

        final boolean isCodeScannerReady = codeScanner != null && !isQrCodeDataReceived;

        if (areCameraAndLocationGranted && isCodeScannerReady) {
            codeScanner.startPreview();
        }

        // This condition is to get event of cancel button of "try again" popup. Because Android 10 is not giving event on cancel button click if network is not found.
        final boolean isProvisioningUsingSoftap = espDevice != null &&
                espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_SOFTAP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isProvisioningUsingSoftap) {
            String ssid = getWifiSsid();
            Log.d(TAG, "SSID of current WiFi network: " + ssid);
            Log.d(TAG, "Device name: " + espDevice.getDeviceName());

            if (!TextUtils.isEmpty(ssid) && !ssid.equals(espDevice.getDeviceName())) {
                Log.e(TAG, "Device is not connected");
                finish();
            }
        }
    }

    /**
     * Stops the camera preview.
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (codeScanner != null) {
            codeScanner.stopPreview();
        }
    }

    @Override
    protected void onDestroy() {
        hideLoadingIndicator();
        EventBus.getDefault().unregister(this);

        if (codeScanner != null) {
            codeScanner.releaseResources();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (provisioningManager.getEspDevice() != null) {
            provisioningManager.getEspDevice().disconnectDevice();
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.e(TAG, "onRequestPermissionsResult , requestCode : " + requestCode);

        if (CAMERA_PERMISSION_REQUEST_CODE == requestCode && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                layoutQrCode.setVisibility(View.VISIBLE);
                layoutPermissionErr.setVisibility(View.GONE);
                openCamera();

            } else {
                findViewById(R.id.scanner_view).setVisibility(View.GONE);
                layoutQrCode.setVisibility(View.GONE);
                layoutPermissionErr.setVisibility(View.VISIBLE);
                permissionErrorText.setText(R.string.error_camera_permission);
                permissionErrorImage.setImageResource(R.drawable.ic_no_camera_permission);
            }
        } else if (ACCESS_FINE_LOCATION_REQUEST_CODE == requestCode && grantResults.length > 0) {

            boolean permissionGranted = true;

            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Log.e(TAG, "User has denied permission");
                    permissionGranted = false;
                }
            }

            CodeScannerView scannerView = findViewById(R.id.scanner_view);

            if (permissionGranted) {
                scannerView.setVisibility(View.VISIBLE);
                layoutQrCode.setVisibility(View.VISIBLE);
                layoutPermissionErr.setVisibility(View.GONE);
                scanQrCode();

            } else {
                scannerView.setVisibility(View.GONE);
                layoutQrCode.setVisibility(View.GONE);
                layoutPermissionErr.setVisibility(View.VISIBLE);
                permissionErrorText.setText(R.string.error_location_permission);
                permissionErrorImage.setImageResource(R.drawable.ic_no_location_permission);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceConnectionEvent event) {

        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case ESPConstants.EVENT_DEVICE_CONNECTED:

                if (TextUtils.isEmpty(provisioningManager.getEspDevice().getUserName())) {
                    String userName = sharedPreferences.getString(AppConstants.KEY_USER_NAME, AppConstants.DEFAULT_USER_NAME);
                    provisioningManager.getEspDevice().setUserName(userName);
                }
                Log.d(TAG, "Device Connected Event Received");
                updateSecurityTypeFromDeviceData();
                break;

            case ESPConstants.EVENT_DEVICE_DISCONNECTED:

                if (espDevice != null && espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_BLE)) {

                    Toast.makeText(ScanQrActivity.this, "Device disconnected", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    if (!isFinishing()) {
                        askForManualDeviceConnection();
                    }
                }
                break;

            case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:

                if (espDevice != null && espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_BLE)) {
                    alertForDeviceNotSupported("Failed to connect with device");
                } else {
                    if (!isFinishing()) {
                        askForManualDeviceConnection();
                    }
                }
                break;
        }
    }

    View.OnClickListener btnAddManuallyClickListener = v -> {

        final DeviceTransportType transportType = getProvisioningTransportFromPrefs();

        if (Utils.doesDeviceSupportBleTransport(transportType)) {

            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bleAdapter = bluetoothManager.getAdapter();

            if (!bleAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !(isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT))) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission is not granted.");
                    return;
                }

                startForResult.launch(enableBtIntent);
            }
        }

        startManualProvisioning();
    };

    private final View.OnClickListener cancelBtnOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            if (provisioningManager.getEspDevice() != null) {
                provisioningManager.getEspDevice().disconnectDevice();
            }
            setResult(RESULT_CANCELED, new Intent());
            finish();
        }
    };

    @SuppressWarnings("Convert2Lambda")
    private final View.OnClickListener btnGetPermissionClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final List<String> requiredPermissions = new ArrayList<>(Arrays.asList(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
            ));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requiredPermissions.addAll(Arrays.asList(
                   Manifest.permission.BLUETOOTH_SCAN,
                   Manifest.permission.BLUETOOTH_CONNECT
                ));
            }

            final List<String> permissionsNotGranted = getMissingPermissions(requiredPermissions);

            if (!permissionsNotGranted.isEmpty()) {
                ActivityCompat.requestPermissions(
                        ScanQrActivity.this,
                        permissionsNotGranted.toArray(new String[0]),
                        ACCESS_FINE_LOCATION_REQUEST_CODE
                );
            }

            if (ActivityCompat.checkSelfPermission(ScanQrActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(ScanQrActivity.this, new
                        String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);

            } else {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                    if (ActivityCompat.checkSelfPermission(ScanQrActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                            || ActivityCompat.checkSelfPermission(ScanQrActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                            || ActivityCompat.checkSelfPermission(ScanQrActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(ScanQrActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT}, ACCESS_FINE_LOCATION_REQUEST_CODE);
                    }
                } else {
                    if (ActivityCompat.checkSelfPermission(ScanQrActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(ScanQrActivity.this, new
                                String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_FINE_LOCATION_REQUEST_CODE);
                    }
                }
            }
        }
    };

    private void initViews() {

        TextView appBarTitleText = findViewById(R.id.main_toolbar_title);
        appBarTitleText.setText(R.string.title_activity_add_device);

        TextView backBtnText = findViewById(R.id.btn_back);
        backBtnText.setVisibility(View.GONE);

        TextView cancelBtnText = findViewById(R.id.btn_cancel);
        cancelBtnText.setVisibility(View.VISIBLE);
        cancelBtnText.setOnClickListener(cancelBtnOnClickListener);

        CodeScannerView scannerView = findViewById(R.id.scanner_view);
        codeScanner = new CodeScanner(this, scannerView);

        loadingIndicator = findViewById(R.id.loader);
        layoutQrCode = findViewById(R.id.layout_qr_code_txt);

        MaterialCardView addDeviceManuallyBtn = findViewById(R.id.btn_add_device_manually);
        addDeviceManuallyBtn.setOnClickListener(btnAddManuallyClickListener);

        TextView addDeviceManuallyBtnText = addDeviceManuallyBtn.findViewById(R.id.text_btn);
        addDeviceManuallyBtnText.setText(R.string.btn_no_qr_code);

        layoutPermissionErr = findViewById(R.id.layout_permission_error);
        permissionErrorText = findViewById(R.id.tv_permission_error);
        permissionErrorImage = findViewById(R.id.iv_permission_error);
        MaterialCardView requestPermissionBtn = findViewById(R.id.btn_get_permission);
        requestPermissionBtn.setOnClickListener(btnGetPermissionClickListener);
        TextView requestPermissionBtnText = findViewById(R.id.text_btn);

        if (requestPermissionBtnText != null) {
            requestPermissionBtnText.setText(R.string.btn_get_permission);
        }

        if (isPermissionGranted(Manifest.permission.CAMERA)) {
            openCamera();
        } else {
            ActivityCompat.requestPermissions(
                    ScanQrActivity.this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void handleActivityResult(ActivityResult result) {
        if (result.getResultCode() == ENABLE_BT_REQUEST_CODE) {
            startManualProvisioning();
        }
    }

    private boolean isPermissionGranted(String permission) {
        final int permissionStatus = ActivityCompat.checkSelfPermission(
            ScanQrActivity.this,
            permission
        );

        return  (PackageManager.PERMISSION_GRANTED == permissionStatus);
    }

    private void openCamera() {
        findViewById(R.id.scanner_view).setVisibility(View.VISIBLE);
        if (codeScanner != null) {
            codeScanner.startPreview();
        }
        scanQrCode();
    }

    @SuppressLint("MissingPermission")
    private void scanQrCode() {

        final List<String> requiredPermissions = Arrays.asList(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.addAll(Arrays.asList(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            ));
        }

        final List<String> permissionsNotGranted = getMissingPermissions(requiredPermissions);

        if (permissionsNotGranted.isEmpty()) {
            provisioningManager.scanQRCode(codeScanner, qrCodeScanListener);
        } else {
            ActivityCompat.requestPermissions(
                    ScanQrActivity.this,
                    permissionsNotGranted.toArray(new String[0]),
                    ACCESS_FINE_LOCATION_REQUEST_CODE
            );
        }
    }

    private List<String> getMissingPermissions(List<String> requiredPermissions) {
        final List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            final int permissionStatus = ActivityCompat.checkSelfPermission(ScanQrActivity.this, permission);

            if (PackageManager.PERMISSION_GRANTED != permissionStatus) {
                missingPermissions.add(permission);
            }
        }

        return missingPermissions;
    }

    private void showLoadingIndicator() {
        loadingIndicator.setVisibility(View.VISIBLE);
        loadingIndicator.show();
    }

    private void hideLoadingIndicator() {
        loadingIndicator.hide();
    }

    private final QRCodeScanListener qrCodeScanListener = new QRCodeScanListener() {

        @SuppressLint("MissingPermission")
        @Override
        public void qrCodeScanned() {
            if (isPermissionGranted(Manifest.permission.VIBRATE)) {
                runOnUiThread(() -> {
                    showLoadingIndicator();
                    Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    vib.vibrate(50);
                    isQrCodeDataReceived = true;
                });
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void deviceDetected(final ESPDevice device) {
            Log.d(TAG, "Device detected: " + (device != null));
            espDevice = device;

//            final DeviceTransportType transportType = getProvisioningTransportFromPrefs();
            final DeviceTransportType transportType = DeviceTransportType.BLE;

            final List<String> requiredPermissions = Arrays.asList(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
            );

            final List<String> permissionsNotGranted = getMissingPermissions(requiredPermissions);

            if (permissionsNotGranted.isEmpty()) {
                runOnUiThread(() -> {

                    if (permissionsNotGranted.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        Log.e(TAG, "Location permission is not granted.");
                        return;
                    }

//                    if (espDevice == null) {
//                        alertForDeviceNotSupported(getString(R.string.error_device_transport_not_supported));
//                        return;
//                    }

                    final ESPConstants.TransportType configuredTransport = (DeviceTransportType.BLE == transportType)
                            ? ESPConstants.TransportType.TRANSPORT_BLE
                            : ESPConstants.TransportType.TRANSPORT_SOFTAP;

                    final boolean doTransportsMatch = espDevice.getTransportType().equals(configuredTransport);

                    Log.i(TAG, "Transports: " + espDevice.getTransportType().name() + ", " + configuredTransport.name());

                    if (doTransportsMatch) {
                        if (DeviceTransportType.SOFTAP == transportType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                            if (!wifiManager.isWifiEnabled()) {
                                alertForWiFi();
                                return;
                            }
                        }

                        device.connectToDevice();
                    } else {
                        alertForDeviceNotSupported(getString(R.string.error_device_transport_not_supported));
                    }
                });
            }
        }

        @Override
        public void onFailure(final Exception e) {
            Log.e(TAG, "Error : " + e.getMessage());

            runOnUiThread(() -> {
                hideLoadingIndicator();
                String msg = e.getMessage();
                Toast.makeText(ScanQrActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            });
        }

        @Override
        public void onFailure(Exception e, String qrCodeData) {
            // Called when QR code is not in supported format.
            // Comment below error handling and do whatever you want to do with your QR code data.
            Log.e(TAG, "Error : " + e.getMessage());
            Log.e(TAG, "QR code data : " + qrCodeData);

            runOnUiThread(() -> {
                hideLoadingIndicator();
                String msg = e.getMessage();
                Toast.makeText(ScanQrActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            });
        }
    };

    private void goToWiFiScanActivity() {
        finish();
        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        startActivity(wifiListIntent);
    }

    private void goToWiFiConfigActivity() {

        finish();
        Intent wifiConfigIntent = new Intent(getApplicationContext(), WiFiConfigActivity.class);
        startActivity(wifiConfigIntent);
    }

    @SuppressLint("MissingPermission")
    private void alertForWiFi() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage(R.string.error_wifi_off);

        builder.setPositiveButton(R.string.btn_ok, (dialog, which) -> {
            final List<String> requiredPermissions = Arrays.asList(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            );

            final List<String> permissionsNotGranted = getMissingPermissions(requiredPermissions);

            if (codeScanner != null) {
                codeScanner.releaseResources();
                codeScanner.startPreview();

                if (permissionsNotGranted.isEmpty()) {
                    provisioningManager.scanQRCode(codeScanner, qrCodeScanListener);
                } else {
                    Log.e(TAG, "Permissions are not granted");
                }
            }

            espDevice = null;

            hideLoadingIndicator();
            dialog.dismiss();
        });

        builder.show();
    }

    private void askForManualDeviceConnection() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);

        builder.setMessage("Unable to connect with device.\nDo you want to connect device manually?");

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, (dialog, which) -> {

            dialog.dismiss();
            if (espDevice != null) {
                navigateToManualSoftApProvisionActivity(espDevice.getSecurityType());
            } else {
                finish();
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, (dialog, which) -> {
            dialog.dismiss();
            finish();
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void startManualProvisioning() {

        final DeviceTransportType transportType = getProvisioningTransportFromPrefs();
        final ESPConstants.SecurityType securityType = getProvisioningSecurityFromPrefs();

        Log.d(TAG, "Configured provisioning transport: " + transportType.name());

        if (DeviceTransportType.BLE == transportType) {
            provisioningManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, securityType);

            navigateToManualBleProvisionActivity(securityType);

        } else if (DeviceTransportType.SOFTAP == transportType){
            provisioningManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, securityType);

            navigateToManualSoftApProvisionActivity(securityType);
        } else {
            final String[] provisioningTransports = {"BLE", "SoftAP"};

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(true);
            builder.setTitle(R.string.dialog_msg_device_selection);

            builder.setItems(provisioningTransports, (dialog, position) -> {
                switch (position) {
                    case 0:
                        provisioningManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, securityType);
                        dialog.dismiss();
                        navigateToManualBleProvisionActivity(securityType);
                        break;

                    case 1:
                        provisioningManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, securityType);
                        dialog.dismiss();
                        navigateToManualSoftApProvisionActivity(securityType);
                        break;
                }
                dialog.dismiss();
            });
            builder.show();
        }
    }

    private void updateSecurityTypeFromDeviceData() {

        ESPConstants.SecurityType deviceSupportedSecurityType = ESPConstants.SecurityType.SECURITY_1;

        String protcolVersionStr = provisioningManager.getEspDevice().getVersionInfo();

        try {
            JSONObject jsonObject = new JSONObject(protcolVersionStr);
            JSONObject provisioningData = jsonObject.getJSONObject("prov");



            if (provisioningData.has(PROV_DATA_SECURITY_VERSION_FIELD)) {
                int deviceSupportedSecurityVersion = provisioningData.optInt(PROV_DATA_SECURITY_VERSION_FIELD);
                Log.d(TAG, "Security version supported by device: " + deviceSupportedSecurityVersion);

                // Ensure deviceSupportedSecurityVersion is between 0 and 2.
                deviceSupportedSecurityVersion = Math.min(2, Math.max(0, deviceSupportedSecurityVersion));

                deviceSupportedSecurityType = ESPConstants.SecurityType.values()[deviceSupportedSecurityVersion];
            }

            provisioningManager.getEspDevice().setSecurityType(deviceSupportedSecurityType);

        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "Capabilities JSON not available.");
        }

        ESPConstants.SecurityType configuredSecType = getProvisioningSecurityFromPrefs();
        configuredSecType = ESPConstants.SecurityType.SECURITY_1;
        Log.i(TAG, "Security types: " + configuredSecType.name() + ", " + deviceSupportedSecurityType.name());

        final boolean isSecurityVersionSupported = configuredSecType == deviceSupportedSecurityType;

        if (isSecurityVersionSupported) {
            navigateToNetworkCredentialsStep();
        } else {
            alertForDeviceNotSupported(getString(R.string.error_security_mismatch));
        }
    }

    private void navigateToNetworkCredentialsStep() {
        final List<String> deviceCapabilities = espDevice.getDeviceCapabilities();

        if (deviceCapabilities.contains(DEVICE_CAPABILITIES_WIFI_SCAN)) {
            goToWiFiScanActivity();
        } else {
            goToWiFiConfigActivity();
        }
    }

    private void navigateToManualBleProvisionActivity(ESPConstants.SecurityType securityType) {
        finish();
        Intent bleProvisioningIntent = new Intent(ScanQrActivity.this, BLEProvisionLanding.class);
        bleProvisioningIntent.putExtra(AppConstants.PREFERENCES_SECURITY_TYPE_KEY, securityType.ordinal());
        startActivity(bleProvisioningIntent);
    }

    private void navigateToManualSoftApProvisionActivity(ESPConstants.SecurityType securityType) {

        finish();
        Intent wifiProvisioningIntent = new Intent(getApplicationContext(), ProvisionLanding.class);
        wifiProvisioningIntent.putExtra(AppConstants.PREFERENCES_SECURITY_TYPE_KEY, securityType.ordinal());

        if (espDevice != null) {
            wifiProvisioningIntent.putExtra(AppConstants.KEY_DEVICE_NAME, espDevice.getDeviceName());
            wifiProvisioningIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, espDevice.getProofOfPossession());
        }
        startActivity(wifiProvisioningIntent);
    }

    private String getWifiSsid() {
        String ssid = null;
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {

            ssid = wifiInfo.getSSID();
            ssid = ssid.replace("\"", "");
        }
        return ssid;
    }

    private void alertForDeviceNotSupported(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle(R.string.error_title);
        builder.setMessage(msg);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, (dialog, which) -> {
            if (provisioningManager.getEspDevice() != null) {
                provisioningManager.getEspDevice().disconnectDevice();
            }
            dialog.dismiss();
            finish();
        });

        builder.show();
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
                AppConstants.PREFERENCES_SECURITY_TYPE_KEY,
                ESPConstants.SecurityType.SECURITY_0.ordinal()
        );

        return ESPConstants.SecurityType.values()[securityTypeOrdinal];
    }
}
