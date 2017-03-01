package com.duvitech.virtualdisplay;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

public class TestDisplay extends Presentation {
    private static final String TAG = "TestDisplay";

    private int mDefaultDisplayOrientation = Surface.ROTATION_0;

    public TestDisplay(Context context, Display display) {
        super(context, display);
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            display = activity.getWindowManager().getDefaultDisplay();
            mDefaultDisplayOrientation = display.getRotation();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_display);

        setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                Log.i(TAG, "onDismiss");
            }
        });
    }
}
