package com.yeapguy.volumescroll;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class VolumeScrollApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
