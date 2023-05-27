package com.espressif.ui.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.espressif.AppConstants;
import com.espressif.ui.models.DeviceConfiguration;
import com.espressif.ui.models.UnitSystem;
import com.espressif.wifi_provisioning.R;


public class DeviceConfigurationActivity extends AppCompatActivity {

    private static final String TAG = DeviceConfigurationActivity.class.getSimpleName();

    final DeviceConfiguration deviceConfiguration = new DeviceConfiguration(
            "",
            0.0,
            0.0,
            "",
            "",
            "en",
            UnitSystem.STANDARD
    );

    EditText apiKeyEditText;
    EditText latitudeEditText;
    EditText longitudeEditText;
    EditText zipCodeEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_configuration);

        initViews();
    }

    private void initViews() {
        //TODO: initialize views.
        final Button continueButton = findViewById(R.id.continue_button);
        continueButton.setOnClickListener(view -> {
            confirmDeviceConfiguration();
        });

        apiKeyEditText = findViewById(R.id.api_key_edit_text);
        latitudeEditText = findViewById(R.id.latitude_edit_text);
        longitudeEditText = findViewById(R.id.longitude_edit_text);
        zipCodeEditText = findViewById(R.id.zip_code_edit_text);

        initSpinners();
    }

    private void confirmDeviceConfiguration() {

        //TODO: Get values from input views.
        final String apiKey = apiKeyEditText.getText().toString();
        deviceConfiguration.setOpenWeatherApiKey(apiKey);

        final String latitudeInputValue = latitudeEditText.getText().toString();
        final String longitudeInputValue = longitudeEditText.getText().toString();

        if (latitudeInputValue.isEmpty() || longitudeInputValue.isEmpty()) {
            final String zipCodeInputValue = zipCodeEditText.getText().toString();
            deviceConfiguration.setZipCode(zipCodeInputValue);
        } else {
            deviceConfiguration.setLatitude(Double.parseDouble(latitudeInputValue));
            deviceConfiguration.setLongitude(Double.parseDouble(longitudeInputValue));

            deviceConfiguration.setZipCode("");
            deviceConfiguration.setCountryCode("");
        }

        navigateToProvisioningStep();
    }

    private void navigateToProvisioningStep() {
        final Intent intent = new Intent(getApplicationContext(), ProvisionActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra(AppConstants.KEY_DEVICE_CONFIGURATION, deviceConfiguration);
        startActivity(intent);
    }

    private void initSpinners() {
        final Spinner countrySpinner = (Spinner) findViewById(R.id.country_dropdown);
        ArrayAdapter<CharSequence> countryAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.countries,
                android.R.layout.simple_spinner_item
        );

        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        countrySpinner.setAdapter(countryAdapter);
        countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                Log.i(TAG, "Item selected in country dropdown: " + adapterView.getItemAtPosition(pos));
                final String selectedValue = adapterView.getItemAtPosition(pos).toString();
                if (selectedValue.equals("Mexico")) {
                    deviceConfiguration.setCountryCode("MX");
                } else if (selectedValue.equals("United States")) {
                    deviceConfiguration.setCountryCode("US");
                } else {
                    deviceConfiguration.setCountryCode("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        final Spinner languageSpinner = (Spinner) findViewById(R.id.language_dropdown);
        ArrayAdapter<CharSequence> languageAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.languages,
                android.R.layout.simple_spinner_item
        );

        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        languageSpinner.setAdapter(languageAdapter);
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                final String selectedValue = adapterView.getItemAtPosition(pos).toString();
                switch (selectedValue) {
                    case "English":
                        deviceConfiguration.setLanguageCode("en");
                        break;
                    case "Español":
                        deviceConfiguration.setLanguageCode("es");
                        break;
                    case "Français":
                        deviceConfiguration.setLanguageCode("fr");
                        break;
                    default:
                        deviceConfiguration.setLanguageCode("");
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        final Spinner unitSystemSpinner = (Spinner) findViewById(R.id.unit_system_dropdown);
        ArrayAdapter<CharSequence> unitSystemOptionsAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.unit_systems,
                android.R.layout.simple_spinner_item
        );

        unitSystemOptionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        unitSystemSpinner.setAdapter(unitSystemOptionsAdapter);
        unitSystemSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                Log.i(TAG, "Item selected in unit system dropdown: " + adapterView.getItemAtPosition(pos));
                final String selectedValue = adapterView.getItemAtPosition(pos).toString();
                switch (selectedValue) {
                    case "Metric":
                        deviceConfiguration.setUnitSystem(UnitSystem.METRIC);
                        break;
                    case "Imperial":
                        deviceConfiguration.setUnitSystem(UnitSystem.IMPERIAL);
                        break;
                    case "Standard":
                    default:
                        deviceConfiguration.setUnitSystem(UnitSystem.STANDARD);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }
}