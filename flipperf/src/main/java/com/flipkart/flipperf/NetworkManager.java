package com.flipkart.flipperf;

import android.net.NetworkInfo;

import com.flipkart.flipperf.model.RequestStats;

/**
 * Created by anirudh.r on 11/05/16 at 3:41 PM.
 */
public interface NetworkManager {
    void onResponseReceived(RequestStats requestStats);

    void onHttpExchangeError(RequestStats requestStats);

    void onResponseInputStreamError(RequestStats requestStats);

    void addListener(OnResponseReceivedListener networkManager);

    void unregisterListener(OnResponseReceivedListener networkManager);

    void setNetworkType(NetworkInfo networkType);

    void setMaxSize(int size);
}