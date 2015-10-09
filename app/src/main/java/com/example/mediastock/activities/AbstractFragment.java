package com.example.mediastock.activities;

import android.content.Context;
import android.net.ConnectivityManager;

/**
 * Created by dinu on 04/10/15.
 */
public abstract class AbstractFragment extends android.support.v4.app.Fragment{

    /**
     * Checks if the device is connected to the Internet
     *
     * @return true if connected, false otherwise
     */
    public boolean isOnline() {
        Context context = this.getActivity().getApplicationContext();

        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnectedOrConnecting();
    }
}
