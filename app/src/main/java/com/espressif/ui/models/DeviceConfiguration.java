package com.espressif.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class DeviceConfiguration implements Parcelable {

    private String openWeatherApiKey;
    private double latitude;
    private double longitude;
    private String zipCode;
    private String countryCode;
    private String languageCode;
    private UnitSystem unitSystem;

    public static final String DEFAULT_LANGUAGE_CODE = "en";
    public static final UnitSystem DEFAULT_UNIT_SYSTEM = UnitSystem.STANDARD;

    public DeviceConfiguration(String apiKey, double latitude, double longitude, String zipCode,
                               String cityName, String languageCode, UnitSystem unitSystem) {
        this.openWeatherApiKey = apiKey;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zipCode = zipCode;
        this.countryCode = cityName;
        this.languageCode = languageCode;
        this.unitSystem = unitSystem;
    }

    protected DeviceConfiguration(Parcel in) {
        openWeatherApiKey = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
        zipCode = in.readString();
        countryCode = in.readString();
        languageCode = in.readString();

        final int unitSystemOrdinal = in.readInt();
        unitSystem = UnitSystem.values()[unitSystemOrdinal];
    }

    public static final Creator<DeviceConfiguration> CREATOR = new Creator<DeviceConfiguration>() {
        @Override
        public DeviceConfiguration createFromParcel(Parcel in) {
            return new DeviceConfiguration(in);
        }

        @Override
        public DeviceConfiguration[] newArray(int size) {
            return new DeviceConfiguration[size];
        }
    };

    public String getOpenWeatherApiKey() {
        return openWeatherApiKey;
    }

    public void setOpenWeatherApiKey(String openWeatherApiKey) {
        this.openWeatherApiKey = openWeatherApiKey;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String cityName) {
        this.countryCode = cityName;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public UnitSystem getUnitSystem() {
        return unitSystem;
    }

    public void setUnitSystem(UnitSystem unitSystem) {
        this.unitSystem = unitSystem;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeString(openWeatherApiKey);
        parcel.writeDouble(latitude);
        parcel.writeDouble(longitude);
        parcel.writeString(zipCode);
        parcel.writeString(countryCode);
        parcel.writeString(languageCode);
        parcel.writeInt(unitSystem.ordinal());
    }
}


