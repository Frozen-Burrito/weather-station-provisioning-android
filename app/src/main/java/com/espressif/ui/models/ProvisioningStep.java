package com.espressif.ui.models;

public enum ProvisioningStep {
    SENDING_CONFIGURATION,
    SENDING_WIFI_CREDENTIALS,
    ATTEMPTING_CONNECTION,
    VERIFYING_STATUS,
    COMPLETE
}
