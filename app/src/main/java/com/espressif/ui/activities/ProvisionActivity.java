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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;

import com.espressif.AppConstants;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.ui.models.DeviceConfiguration;
import com.espressif.ui.models.ProvisioningStep;
import com.espressif.ui.models.WiFiCredentials;
import com.espressif.wifi_provisioning.R;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.listeners.ProvisionListener;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProvisionActivity extends AppCompatActivity {

    private static final String TAG = ProvisionActivity.class.getSimpleName();

    // Views
    final List<ImageView> stepImages = new ArrayList<>();
    final List<ContentLoadingProgressBar> progressIndicators = new ArrayList<>();
    final List<TextView> stepErrorTexts = new ArrayList<>();

    private TextView provisioningErrorText;
    private CardView continueButton;

    // Provisioning data
    private WiFiCredentials wifiCredentials;
    private DeviceConfiguration deviceConfiguration;

    private ESPProvisionManager provisioningManager;
    private ProvisioningStep provisioningStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provision);

        Intent intent = getIntent();
        wifiCredentials = intent.getParcelableExtra(AppConstants.KEY_WIFI_CREDENTIALS);
        deviceConfiguration = intent.getParcelableExtra(AppConstants.KEY_DEVICE_CONFIGURATION);

        provisioningManager = ESPProvisionManager.getInstance(getApplicationContext());

        initViews();

        EventBus.getDefault().register(this);

        Log.d(TAG, "Selected AP -" + wifiCredentials.getSsid());
        disableContinueButton();
        sendDeviceConfiguration();
    }

    @Override
    public void onBackPressed() {
        provisioningManager.getEspDevice().disconnectDevice();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceConnectionEvent event) {

        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case ESPConstants.EVENT_DEVICE_DISCONNECTED:
                if (!isFinishing() && ProvisioningStep.COMPLETE != provisioningStep) {
                    showAlertForDeviceDisconnected();
                }
                break;
        }
    }

    private final View.OnClickListener okBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            provisioningManager.getEspDevice().disconnectDevice();
            finish();
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
        }
    };

    private void initViews() {
        TextView titleText = findViewById(R.id.main_toolbar_title);
        titleText.setText(R.string.title_activity_provisioning);

        TextView backButtonText = findViewById(R.id.btn_back);
        backButtonText.setVisibility(View.GONE);

        TextView cancelButtonText = findViewById(R.id.btn_cancel);
        cancelButtonText.setVisibility(View.GONE);

        stepImages.addAll(Arrays.asList(
                findViewById(R.id.iv_tick_1),
                findViewById(R.id.iv_tick_2),
                findViewById(R.id.iv_tick_3),
                findViewById(R.id.iv_tick_4)
        ));

        progressIndicators.addAll(Arrays.asList(
                findViewById(R.id.prov_progress_1),
                findViewById(R.id.prov_progress_2),
                findViewById(R.id.prov_progress_3),
                findViewById(R.id.prov_progress_4)
        ));

        stepErrorTexts.addAll(Arrays.asList(
                findViewById(R.id.tv_prov_error_1),
                findViewById(R.id.tv_prov_error_2),
                findViewById(R.id.tv_prov_error_3),
                findViewById(R.id.tv_prov_error_4)
        ));

        provisioningErrorText = findViewById(R.id.tv_prov_error);

        continueButton = findViewById(R.id.btn_ok);
        continueButton.findViewById(R.id.iv_arrow).setVisibility(View.GONE);
        continueButton.setOnClickListener(okBtnClickListener);

        TextView continueBtnText = findViewById(R.id.text_btn);
        continueBtnText.setText(R.string.btn_done);
    }

    private void sendDeviceConfiguration() {
        provisioningStep = ProvisioningStep.SENDING_CONFIGURATION;
        stepImages.get(provisioningStep.ordinal()).setVisibility(View.GONE);
        progressIndicators.get(provisioningStep.ordinal()).setVisibility(View.VISIBLE);

        final JSONObject deviceConfigJson = new JSONObject();
        try {
            deviceConfigJson.put("apiKey", deviceConfiguration.getOpenWeatherApiKey());


            deviceConfigJson.put("lat", deviceConfiguration.getLatitude());
            deviceConfigJson.put("lon", deviceConfiguration.getLongitude());
            deviceConfigJson.put("zipcode", deviceConfiguration.getZipCode());
            deviceConfigJson.put("country", deviceConfiguration.getCountryCode());

            if (!DeviceConfiguration.DEFAULT_LANGUAGE_CODE.equals(deviceConfiguration.getLanguageCode())) {
                deviceConfigJson.put("lang", deviceConfiguration.getLanguageCode());
            }

            if (DeviceConfiguration.DEFAULT_UNIT_SYSTEM != deviceConfiguration.getUnitSystem()) {
                deviceConfigJson.put("units", deviceConfiguration.getUnitSystem().ordinal());
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "Configuration in JSON format: " + deviceConfigJson);

        provisioningManager.getEspDevice().sendDataToCustomEndPoint(
                AppConstants.CUSTOM_CONFIG_ENDPOINT,
                deviceConfigJson.toString().getBytes(StandardCharsets.UTF_8),
                new ResponseListener() {

                    @Override
                    public void onSuccess(byte[] returnData) {
                        Log.e(TAG, "Configuration provisioning success, response: " + new String(returnData, StandardCharsets.UTF_8));
                        provisioningStep = ProvisioningStep.SENDING_WIFI_CREDENTIALS;

                        runOnUiThread(() -> {
                            final int currentStepIndex = provisioningStep.ordinal();
                            final int previousStepIndex = currentStepIndex - 1;

                            stepImages.get(previousStepIndex).setImageResource(R.drawable.ic_checkbox_on);
                            stepImages.get(previousStepIndex).setVisibility(View.VISIBLE);
                            progressIndicators.get(previousStepIndex).setVisibility(View.GONE);

                            progressIndicators.get(currentStepIndex).setVisibility(View.VISIBLE);
                            stepImages.get(currentStepIndex).setVisibility(View.GONE);
                        });

                        sendWifiCredentials();
                    }

                    @Override
                    public void onFailure(Exception e) {

                        Log.w(TAG, "Configuration provisioning failure, exception" + e.getMessage());

                        runOnUiThread(() -> {
                            final int currentStepIndex = provisioningStep.ordinal();
                            stepImages.get(currentStepIndex).setImageResource(R.drawable.ic_error);
                            stepImages.get(currentStepIndex).setVisibility(View.VISIBLE);
                            progressIndicators.get(currentStepIndex).setVisibility(View.GONE);
                            stepErrorTexts.get(currentStepIndex).setVisibility(View.VISIBLE);
                            stepErrorTexts.get(currentStepIndex).setText(R.string.error_prov_step_4);
                            provisioningErrorText.setVisibility(View.VISIBLE);
                            enableContinueButton();
                        });
                    }
                }
        );
    }

//    private void updateStepIndicatorViews(String errorMessage) {
//        final int currentStepIndex = provisioningStep.ordinal();
//
//        stepImages.get(currentStepIndex).setVisibility(View.GONE);
//        progressIndicators.get()
//    }

    private void sendWifiCredentials() {

        ESPDevice device = provisioningManager.getEspDevice();
        Log.e(TAG, "Provisioning wifi credentials");
        device.provision(wifiCredentials.getSsid(), wifiCredentials.getPassword(), new ProvisionListener() {

            @Override
            public void createSessionFailed(Exception e) {

                runOnUiThread(() -> {
                    final int currentStepIndex = provisioningStep.ordinal();

                    stepImages.get(currentStepIndex).setImageResource(R.drawable.ic_error);
                    stepImages.get(currentStepIndex).setVisibility(View.VISIBLE);
                    progressIndicators.get(currentStepIndex).setVisibility(View.GONE);
                    stepErrorTexts.get(currentStepIndex).setVisibility(View.VISIBLE);
                    stepErrorTexts.get(currentStepIndex).setText(R.string.error_session_creation);
                    provisioningErrorText.setVisibility(View.VISIBLE);
                    enableContinueButton();
                });
            }

            @Override
            public void wifiConfigSent() {

                runOnUiThread(() -> {
                    provisioningStep = ProvisioningStep.ATTEMPTING_CONNECTION;
                    final int currentStepIndex = provisioningStep.ordinal();

                    final int previousStepIndex = currentStepIndex - 1;
                    stepImages.get(previousStepIndex).setImageResource(R.drawable.ic_checkbox_on);
                    stepImages.get(previousStepIndex).setVisibility(View.VISIBLE);
                    progressIndicators.get(previousStepIndex).setVisibility(View.GONE);

                    stepImages.get(currentStepIndex).setVisibility(View.GONE);
                    progressIndicators.get(currentStepIndex).setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void wifiConfigFailed(Exception e) {

                runOnUiThread(() -> {
                    final int currentStepIndex = provisioningStep.ordinal();
                    stepImages.get(currentStepIndex).setImageResource(R.drawable.ic_error);
                    stepImages.get(currentStepIndex).setVisibility(View.VISIBLE);
                    progressIndicators.get(currentStepIndex).setVisibility(View.GONE);
                    stepErrorTexts.get(currentStepIndex).setVisibility(View.VISIBLE);
                    stepErrorTexts.get(currentStepIndex).setText(R.string.error_prov_step_1);
                    provisioningErrorText.setVisibility(View.VISIBLE);
                    enableContinueButton();
                });
            }

            @Override
            public void wifiConfigApplied() {

                runOnUiThread(() -> {
                    provisioningStep = ProvisioningStep.VERIFYING_STATUS;
                    final int currentStepIndex = provisioningStep.ordinal();

                    final int previousStepIndex = currentStepIndex - 1;
                    stepImages.get(previousStepIndex).setImageResource(R.drawable.ic_checkbox_on);
                    stepImages.get(previousStepIndex).setVisibility(View.VISIBLE);
                    progressIndicators.get(previousStepIndex).setVisibility(View.GONE);

                    stepImages.get(currentStepIndex).setVisibility(View.GONE);
                    progressIndicators.get(currentStepIndex).setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void wifiConfigApplyFailed(Exception e) {

                runOnUiThread(() -> {
                    final int currentStepIndex = provisioningStep.ordinal();
                    stepImages.get(currentStepIndex).setImageResource(R.drawable.ic_error);
                    stepImages.get(currentStepIndex).setVisibility(View.VISIBLE);
                    progressIndicators.get(currentStepIndex).setVisibility(View.GONE);
                    stepErrorTexts.get(currentStepIndex).setVisibility(View.VISIBLE);
                    stepErrorTexts.get(currentStepIndex).setText(R.string.error_prov_step_2);
                    provisioningErrorText.setVisibility(View.VISIBLE);
                    enableContinueButton();
                });
            }

            @Override
            public void provisioningFailedFromDevice(final ESPConstants.ProvisionFailureReason failureReason) {

                runOnUiThread(() -> {
                    final int currentStepIndex = provisioningStep.ordinal();
                    switch (failureReason) {
                        case AUTH_FAILED:
                            stepErrorTexts.get(currentStepIndex).setText(R.string.error_authentication_failed);
                            break;
                        case NETWORK_NOT_FOUND:
                            stepErrorTexts.get(currentStepIndex).setText(R.string.error_network_not_found);
                            break;
                        case DEVICE_DISCONNECTED:
                        case UNKNOWN:
                            stepErrorTexts.get(currentStepIndex).setText(R.string.error_prov_step_3);
                            break;
                    }

                    stepImages.get(currentStepIndex).setImageResource(R.drawable.ic_error);
                    stepImages.get(currentStepIndex).setVisibility(View.VISIBLE);
                    progressIndicators.get(currentStepIndex).setVisibility(View.GONE);
                    stepErrorTexts.get(currentStepIndex).setVisibility(View.VISIBLE);
                    provisioningErrorText.setVisibility(View.VISIBLE);
                    enableContinueButton();
                });
            }

            @Override
            public void deviceProvisioningSuccess() {

                runOnUiThread(() -> {
                    provisioningStep = ProvisioningStep.COMPLETE;
                    final int previousStepIndex = provisioningStep.ordinal() - 1;
                    stepImages.get(previousStepIndex).setImageResource(R.drawable.ic_checkbox_on);
                    stepImages.get(previousStepIndex).setVisibility(View.VISIBLE);
                    progressIndicators.get(previousStepIndex).setVisibility(View.GONE);
                    enableContinueButton();
                });
            }

            @Override
            public void onProvisioningFailed(Exception e) {

                runOnUiThread(() -> {
                    final int currentStepIndex = provisioningStep.ordinal();
                    stepImages.get(currentStepIndex).setImageResource(R.drawable.ic_error);
                    stepImages.get(currentStepIndex).setVisibility(View.VISIBLE);
                    progressIndicators.get(currentStepIndex).setVisibility(View.GONE);
                    stepErrorTexts.get(currentStepIndex).setVisibility(View.VISIBLE);
                    stepErrorTexts.get(currentStepIndex).setText(R.string.error_prov_step_3);
                    provisioningErrorText.setVisibility(View.VISIBLE);
                    enableContinueButton();
                });
            }
        });
    }

    private void disableContinueButton() {
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.5f);
    }

    public void enableContinueButton() {
        continueButton.setEnabled(true);
        continueButton.setAlpha(1f);
    }

    private void showAlertForDeviceDisconnected() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(R.string.error_title);
        builder.setMessage(R.string.dialog_msg_ble_device_disconnection);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, (dialog, which) -> {
            dialog.dismiss();
            finish();
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
        });

        builder.show();
    }
}
