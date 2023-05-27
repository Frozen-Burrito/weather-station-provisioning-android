package com.espressif.ui.models;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

public class WiFiCredentials implements Parcelable {

    private String ssid;
    private String password;

    public WiFiCredentials(String ssid, String password) {
        this.ssid = ssid;
        this.password = password;
    }

    protected WiFiCredentials(Parcel in) {
        ssid = in.readString();
        password = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeString(ssid);
        parcel.writeString(password);
    }

    public static final Creator<WiFiCredentials> CREATOR = new Creator<WiFiCredentials>() {
        @Override
        public WiFiCredentials createFromParcel(Parcel in) {
            return new WiFiCredentials(in);
        }

        @Override
        public WiFiCredentials[] newArray(int size) {
            return new WiFiCredentials[size];
        }
    };

    //IMPORTANT: Standard "is..." method names are not used because it simplifies JSON serialization.
    public boolean ssidIsEmpty() { return TextUtils.isEmpty(ssid); }
    public boolean passwordIsEmpty() { return TextUtils.isEmpty(password); }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @NonNull
    @Override
    public String toString() {
        return "Credentials for network with SSID = " + ssid;
    }
}
