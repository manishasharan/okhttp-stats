/*
 * The MIT License
 *
 * Copyright 2016 Flipkart Internet Pvt. Ltd.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom
 * the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.flipkart.okhttpstats.handler;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;

import com.flipkart.okhttpstats.model.RequestStats;
import com.flipkart.okhttpstats.toolbox.NetworkStat;
import com.flipkart.okhttpstats.toolbox.PreferenceManager;
import com.flipkart.okhttpstats.toolbox.Utils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Default implementation of {@link NetworkRequestStatsHandler}
 * <p>
 * Responsibilities:
 * <p>
 * 1. Allows to register/deregister listeners, and gives callback to all the registered listeners in case of success or errors
 * 2. Gives the current network info for a particular request
 * 3. Gives the network speed based upon the type of current network
 * 4. Allows to calculate the average network speed, and save it to {@link android.content.SharedPreferences} to retrieve it later
 */
public class PersistentStatsHandler implements NetworkRequestStatsHandler {

    private static final int DEFAULT_MAX_SIZE = 10;
    private static final String WIFI_NETWORK = "WIFI";
    private static final String MOBILE_NETWORK = "mobile";
    private static final String UNKNOWN_NETWORK = "unknown";
    private final PreferenceManager mPreferenceManager;
    final Set<OnResponseListener> mOnResponseListeners = new HashSet<>();
    private int mResponseCount = 0;
    private int MAX_SIZE;
    private final WifiManager mWifiManager;
    private final NetworkStat mNetworkStat;
    private float mCurrentAvgSpeed;
    private final ConnectivityManager mConnectivityManager;

    public PersistentStatsHandler(Context context) {
        this.mPreferenceManager = new PreferenceManager(context);
        this.MAX_SIZE = DEFAULT_MAX_SIZE;
        this.mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.mNetworkStat = new NetworkStat();
        this.mCurrentAvgSpeed = mPreferenceManager.getAverageSpeed(getNetworkKey(getActiveNetworkInfo()));
    }

    @VisibleForTesting
    PersistentStatsHandler(Context context, PreferenceManager preferenceManager) {
        this.mPreferenceManager = preferenceManager;
        this.MAX_SIZE = DEFAULT_MAX_SIZE;
        this.mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.mNetworkStat = new NetworkStat();
        this.mCurrentAvgSpeed = mPreferenceManager.getAverageSpeed(getNetworkKey(getActiveNetworkInfo()));
    }

    /**
     * Client can call this to get the current network info
     *
     * @return {@link NetworkInfo}
     */
    public NetworkInfo getActiveNetworkInfo() {
        if (mConnectivityManager != null) {
            try {
                return mConnectivityManager.getActiveNetworkInfo();
            } catch (SecurityException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Client can add listeners to listen for the callbacks.
     *
     * @param onResponseListener : {@link OnResponseListener}
     */
    public void addListener(OnResponseListener onResponseListener) {
        if (mOnResponseListeners != null) {
            mOnResponseListeners.add(onResponseListener);
        }
    }

    /**
     * Client can remove listeners
     *
     * @param onResponseListener : {@link OnResponseListener}
     */
    public void removeListener(OnResponseListener onResponseListener) {
        if (mOnResponseListeners != null) {
            mOnResponseListeners.remove(onResponseListener);
        }
    }

    /**
     * The client can set the max number of request before it stores the speed to shared preference
     *
     * @param size : int
     */
    public void setMaxSizeForPersistence(int size) {
        this.MAX_SIZE = size;
    }

    /**
     * Exposed to the client to get the average network speed
     *
     * @return avg speed
     */
    public float getAverageNetworkSpeed() {
        return mCurrentAvgSpeed;
    }

    @Override
    public void onResponseReceived(final RequestStats requestStats) {
        if (Utils.isLoggingEnabled) {
            Log.d("Response Received : ", requestStats + " ");
        }

        //call all the registered listeners
        for (OnResponseListener onResponseListener : mOnResponseListeners) {
            if (onResponseListener != null) {
                onResponseListener.onResponseSuccess(getActiveNetworkInfo(), requestStats);
            }
        }

        //save to shared prefs if condition is satisfied
        synchronized (this) {
            mResponseCount += 1;
            if (mResponseCount >= MAX_SIZE) {
                //calculate the new average speed
                double newAvgSpeed = mNetworkStat.mCurrentAvgSpeed;
                mCurrentAvgSpeed = (float) ((mCurrentAvgSpeed + newAvgSpeed) / 2);
                //save it in shared preference
                String networkKey = getNetworkKey(getActiveNetworkInfo());
                mPreferenceManager.setAverageSpeed(networkKey, mCurrentAvgSpeed);
                //reset the response count
                mResponseCount = 0;
            }
        }

        mNetworkStat.addRequestStat(requestStats);
    }

    @Override
    public void onHttpExchangeError(RequestStats requestStats, IOException e) {
        if (Utils.isLoggingEnabled) {
            Log.d("Response Http Error :", requestStats + "");
        }

        for (OnResponseListener onResponseListener : mOnResponseListeners) {
            if (onResponseListener != null) {
                onResponseListener.onResponseError(getActiveNetworkInfo(), requestStats, e);
            }
        }
    }

    @Override
    public void onResponseInputStreamError(RequestStats requestStats, Exception e) {
        if (Utils.isLoggingEnabled) {
            Log.d("Response InputStream : ", requestStats + "");
        }

        for (OnResponseListener onResponseListener : mOnResponseListeners) {
            if (onResponseListener != null) {
                onResponseListener.onResponseError(getActiveNetworkInfo(), requestStats, e);
            }
        }
    }

    /**
     * Generates the network key based on the type of network
     *
     * @param networkInfo {@link NetworkInfo}
     * @return string
     */
    @VisibleForTesting
    String getNetworkKey(NetworkInfo networkInfo) {
        if (networkInfo != null && networkInfo.getTypeName() != null) {
            if (networkInfo.getTypeName().equals(WIFI_NETWORK)) {
                return WIFI_NETWORK + "_" + getWifiSSID();
            } else if (networkInfo.getTypeName().equals(MOBILE_NETWORK)) {
                return MOBILE_NETWORK + "_" + networkInfo.getSubtypeName();
            }
            return UNKNOWN_NETWORK;
        }
        return UNKNOWN_NETWORK;
    }

    @VisibleForTesting
    int getWifiSSID() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            String ssid = wifiInfo.getSSID();
            if (!TextUtils.isEmpty(ssid)) {
                return ssid.hashCode();
            }
        }
        return -1;
    }
}