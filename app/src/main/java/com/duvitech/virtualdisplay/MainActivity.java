package com.duvitech.virtualdisplay;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MediaProjectionDemo";
    private static final int PERMISSION_CODE = 1;
    private static final int FRAMERATE = 30;
    private static final List<Resolution> RESOLUTIONS = new ArrayList<Resolution>() {{
        add(new Resolution(640,400));
    }};

    private int mDisplayWidth;
    private int mDisplayHeight;
    private boolean mScreenSharing;
    private File mFile;

    private int mScreenDensity;
    private DisplayManager mDisplayManager;
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;

    private VirtualDisplay mVirtualDisplay;
    private Surface mSurface;
    private SurfaceView mSurfaceView;
    private ToggleButton mToggle;
    private ImageReader imageReader;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    ImageReader.OnImageAvailableListener myImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageGrabber(reader.acquireNextImage(), mFile));
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFile = new File(this.getExternalFilesDir(null), "pic.jpg");
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mDisplayWidth = 640;
        mDisplayHeight = 400;

        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
        imageReader = ImageReader.newInstance(mDisplayWidth, mDisplayHeight, PixelFormat.RGBA_8888, 8);
        imageReader.setOnImageAvailableListener(myImageListener, mBackgroundHandler);
        mSurface = imageReader.getSurface();

        //mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        //mSurface = mSurfaceView.getHolder().getSurface();
        mProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mToggle = (ToggleButton) findViewById(R.id.screen_sharing_toggle);

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "User denied screen sharing permission", Toast.LENGTH_SHORT).show();
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(new MediaProjectionCallback(), null);
        mVirtualDisplay = createVirtualDisplay();
    }

    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            shareScreen();
        } else {
            stopScreenSharing();
        }
    }

    private void shareScreen() {
        mScreenSharing = true;
        if (mSurface == null) {
            return;
        }
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(),
                    PERMISSION_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
    }
    private void stopScreenSharing() {
        if (mToggle.isChecked()) {
            mToggle.setChecked(false);
        }
        mScreenSharing = false;
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("ScreenSharingDemo",
                mDisplayWidth, mDisplayHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                mSurface, null /*Callbacks*/, null /*Handler*/);
    }
    private class ResolutionSelector implements Spinner.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
            Resolution r = (Resolution) parent.getItemAtPosition(pos);
            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE) {
                mDisplayHeight = r.y;
                mDisplayWidth = r.x;
            } else {
                mDisplayHeight = r.x;
                mDisplayWidth = r.y;
            }
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) { /* Ignore */ }
    }

    private void resizeVirtualDisplay() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.resize(mDisplayWidth, mDisplayHeight, mScreenDensity);
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            mMediaProjection = null;
            stopScreenSharing();
        }
    }

    private class SurfaceCallbacks implements SurfaceHolder.Callback {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mDisplayWidth = width;
            mDisplayHeight = height;
            resizeVirtualDisplay();
        }
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurface = holder.getSurface();
            if (mScreenSharing) {
                shareScreen();
            }
        }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (!mScreenSharing) {
                stopScreenSharing();
            }
        }
    }

    private static class Resolution {
        int x;
        int y;
        public Resolution(int x, int y) {
            this.x = x;
            this.y = y;
        }
        @Override
        public String toString() {
            return x + "x" + y;
        }
    }


    private static class ImageGrabber implements Runnable {
        /**
         * The JPEG image
         */
        private final Image mImage;
        private final File mFile;
        private Bitmap bitmap;

        public ImageGrabber(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            Log.d(TAG, "Captured Image " + mImage.getWidth() + "x" + mImage.getHeight() + " Format: " + mImage.getFormat());
            final ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();

            int width = mImage.getWidth();
            int height = mImage.getHeight();
            int pixelStride =  mImage.getPlanes()[0].getPixelStride();
            int rowStride =  mImage.getPlanes()[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;


            bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {

        private boolean mNewDisplayAdded = false;
        private int mCurrentDisplayId = -1;
        private TestDisplay mPresentation;

        @Override
        public void onDisplayAdded(int i) {
            Log.d(TAG, "onDisplayAdded id=" + i);
            if (!mNewDisplayAdded && mCurrentDisplayId == -1) {
                mNewDisplayAdded = true;
                mCurrentDisplayId = i;
            }
        }

        @Override
        public void onDisplayRemoved(int i) {
            Log.d(TAG, "onDisplayRemoved id=" + i);
            if (mCurrentDisplayId == i) {
                mNewDisplayAdded = false;
                mCurrentDisplayId = -1;
                if (mPresentation != null) {
                    mPresentation.dismiss();
                    mPresentation = null;
                }
            }
        }

        @Override
        public void onDisplayChanged(int i) {
            Log.d(TAG, "onDisplayChanged id=" + i);
            if (mCurrentDisplayId == i) {
                if (mNewDisplayAdded) {
                    // create a presentation
                   mNewDisplayAdded = false;
                   Display display = mDisplayManager.getDisplay(i);
                   mPresentation = new TestDisplay(MainActivity.this, display);
                   mPresentation.show();
                }
            }
        }
    };
}
