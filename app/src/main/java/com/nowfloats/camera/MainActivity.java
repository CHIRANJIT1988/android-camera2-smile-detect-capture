package com.nowfloats.camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.nowfloats.camera.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements FaceGraphic.ISmileDetector
{
    private static final String TAG = MainActivity.class.getSimpleName();

    public static final String MEDIA_DIRECTORY_NAME = "NowFloats";

    private Context context;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;

    private int CAPTURE_DELAY = 3;
    private Timer timeTicker;

    private ImageView ivAutoFocus;

    // CAMERA VERSION TWO DECLARATIONS
    private Camera2Source mCamera2Source = null;

    // COMMON TO BOTH CAMERAS
    private CameraSourcePreview mPreview;
    private FaceDetector previewFaceDetector = null;
    private GraphicOverlay mGraphicOverlay;
    private FaceGraphic mFaceGraphic;
    private boolean wasActivityResumed = false;

    private ImageView ibCapture;
    private ImageView ibSwitch;
    private TextView tvTimer;

    // DEFAULT CAMERA BEING OPENED
    private boolean usingFrontCamera = true;

    private OverlayView mOverlayView;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();

        findViewById();

        if(checkGooglePlayAvailability())
        {
            requestPermissionThenOpenCamera();

            switchButtonCallback();
            captureButtonCallback();;

            mPreview.setOnTouchListener(CameraPreviewTouchListener);
        }
    }



    private void findViewById()
    {
        mPreview = findViewById(R.id.preview);
        mGraphicOverlay = findViewById(R.id.faceOverlay);
        ivAutoFocus = findViewById(R.id.ivAutoFocus);

        mOverlayView = findViewById(R.id.overlay_view);
        tvTimer = findViewById(R.id.tvTimer);

        ibCapture = findViewById(R.id.ibCapture);
        ibSwitch = findViewById(R.id.ibSwitch);
    }


    private void switchButtonCallback()
    {
        ibSwitch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v)
            {
                if(usingFrontCamera)
                {
                    stopCameraSource();
                    createCameraSourceBack();
                    usingFrontCamera = false;
                }

                else
                {
                    stopCameraSource();
                    createCameraSourceFront();
                    usingFrontCamera = true;
                }
            }
        });
    }


    private void captureButtonCallback()
    {
        ibCapture.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v)
            {
                ibSwitch.setEnabled(false);
                ibCapture.setEnabled(false);

                if(mCamera2Source.isLockFocus())
                {
                    return;
                }

                mCamera2Source.setLockFocus(true);
                takePicture();
            }
        });
    }


    final Camera2Source.ShutterCallback camera2SourceShutterCallback = new Camera2Source.ShutterCallback() {

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onShutter()
        {
            Log.d(TAG, "Shutter Callback for CAMERA2");
        }
    };


    final Camera2Source.PictureCallback camera2SourcePictureCallback = new Camera2Source.PictureCallback() {

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onPictureTaken(Image image)
        {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            Bitmap picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);

            saveImage(picture);

            image.close();
        }
    };


    private void saveImage(Bitmap picture)
    {
        Log.d(TAG, "Taken picture is here!");

        runOnUiThread(new Runnable() {

            @Override
            public void run()
            {
                ibSwitch.setEnabled(true);
                ibCapture.setEnabled(true);
            }
        });

        FileOutputStream out = null;

        try
        {
            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(), MEDIA_DIRECTORY_NAME);

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists())
            {
                if (!mediaStorageDir.mkdirs())
                {
                    return;
                }
            }

            //create a new file, specifying the path, and the filename
            //which we want to save the file as.
            File file = new File(mediaStorageDir, "NF_" + System.currentTimeMillis() + "_PIC.jpg");

            out = new FileOutputStream(file);
            picture.compress(Bitmap.CompressFormat.JPEG, 100, out);
        }

        catch (Exception e)
        {
            e.printStackTrace();
        }

        finally
        {
            try
            {
                if (out != null)
                {
                    out.close();
                }
            }

            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }


    private boolean checkGooglePlayAvailability()
    {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();

        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);

        if(resultCode == ConnectionResult.SUCCESS)
        {
            return true;
        }

        else if(googleApiAvailability.isUserResolvableError(resultCode))
        {
            googleApiAvailability.getErrorDialog(MainActivity.this, resultCode, 2404).show();
        }

        return false;
    }


    private void requestPermissionThenOpenCamera()
    {
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                createCameraSourceFront();
            }

            else
            {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }

        else
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }


    private void createCameraSourceFront()
    {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational())
        {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());
        }

        else
        {
            Toast.makeText(context, "Face Detection Not Available", Toast.LENGTH_SHORT).show();
        }

        mCamera2Source = new Camera2Source.Builder(context, previewFaceDetector)
                .setFocusMode(/*Camera2Source.CAMERA_AF_AUTO*/ CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL)
                .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                .setFacing(Camera2Source.CAMERA_FACING_FRONT)
                .build();

        mCamera2Source.setOverlayCallback(overlayViewCallback);

        //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
        //WE WILL USE CAMERA1.
        if(mCamera2Source.isCamera2Native())
        {
            startCameraSource();
        }

        else
        {
            if(usingFrontCamera) createCameraSourceFront(); else createCameraSourceBack();
        }
    }


    private void createCameraSourceBack()
    {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational())
        {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());
        }

        else
        {
            Toast.makeText(context, "Face Detection Not Available", Toast.LENGTH_SHORT).show();
        }


        mCamera2Source = new Camera2Source.Builder(context, previewFaceDetector)
                .setFocusMode(/*Camera2Source.CAMERA_AF_AUTO*/ CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL)
                .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                .setFacing(Camera2Source.CAMERA_FACING_BACK)
                .build();

        //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
        //WE WILL USE CAMERA1.
        if(mCamera2Source.isCamera2Native())
        {
            startCameraSource();
        }

        else
        {
            if(usingFrontCamera) createCameraSourceFront(); else createCameraSourceBack();
        }
    }


    private void startCameraSource()
    {
        if(mCamera2Source != null)
        {
            try
            {
                mPreview.start(mCamera2Source, mGraphicOverlay);
            }

            catch (IOException e)
            {
                Log.e(TAG, "Unable to start camera source 2.", e);
                mCamera2Source.release();
                mCamera2Source = null;
            }
        }
    }


    private void stopCameraSource()
    {
        mPreview.stop();
    }


    private void takePicture()
    {
        //Play default capture sound
        MediaActionSound sound = new MediaActionSound();
        sound.play(MediaActionSound.SHUTTER_CLICK);

        if(mCamera2Source != null) mCamera2Source.takePicture(camera2SourceShutterCallback, camera2SourcePictureCallback);
    }

    private void capture()
    {
        Log.i(TAG, "Timer - " + CAPTURE_DELAY);

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                tvTimer.setText(String.valueOf(CAPTURE_DELAY));
                CAPTURE_DELAY--;
            }
        });

        if(CAPTURE_DELAY == -1)
        {
            if(timeTicker != null)
            {
                timeTicker.cancel();
            }

            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    CAPTURE_DELAY = 3;
                    tvTimer.setVisibility(View.GONE);
                }
            });

            takePicture();
        }
    }


    @Override
    public void smiled() {

        if(mCamera2Source == null)
        {
            return;
        }

        if(mCamera2Source.isLockFocus())
        {
            return;
        }

        mCamera2Source.setLockFocus(true);

        //Play focus complete sound
        MediaActionSound sound = new MediaActionSound();
        sound.play(MediaActionSound.FOCUS_COMPLETE);

        tvTimer.setVisibility(View.VISIBLE);

        timeTicker = new Timer();

        timeTicker.scheduleAtFixedRate(new TimerTask() {

              @Override
              public void run()
              {
                  capture();
              }
          }, 0,1000);
    }


    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {

        @Override
        public Tracker<Face> create(Face face)
        {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    private class GraphicFaceTracker extends Tracker<Face> {

        private GraphicOverlay mOverlay;

        GraphicFaceTracker(GraphicOverlay overlay)
        {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay, MainActivity.this, context);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item)
        {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face)
        {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults)
        {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone()
        {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
        }
    }


    private final CameraSourcePreview.OnTouchListener CameraPreviewTouchListener = new CameraSourcePreview.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent pEvent)
        {
            v.onTouchEvent(pEvent);

            if (pEvent.getAction() == MotionEvent.ACTION_DOWN)
            {
                int autoFocusX = (int) (pEvent.getX() - Utils.dpToPx(60)/2);
                int autoFocusY = (int) (pEvent.getY() - Utils.dpToPx(60)/2);

                ivAutoFocus.setTranslationX(autoFocusX);
                ivAutoFocus.setTranslationY(autoFocusY);
                ivAutoFocus.setVisibility(View.VISIBLE);
                ivAutoFocus.bringToFront();

                if(mCamera2Source != null)
                {
                    mCamera2Source.autoFocus(new Camera2Source.AutoFocusCallback() {

                        @Override
                        public void onAutoFocus(boolean success)
                        {
                            runOnUiThread(new Runnable()
                            {
                                @Override public void run()
                                {
                                    ivAutoFocus.setVisibility(View.GONE);
                                }
                            });
                        }
                    }, pEvent, v.getWidth(), v.getHeight());
                }

                else
                {
                    ivAutoFocus.setVisibility(View.GONE);
                }
            }

            return false;
        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {

        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                requestPermissionThenOpenCamera();
            }

            else
            {
                Toast.makeText(MainActivity.this, "Camera Permission Required", Toast.LENGTH_LONG).show();
                finish();
            }
        }

        if(requestCode == REQUEST_STORAGE_PERMISSION)
        {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                requestPermissionThenOpenCamera();
            }

            else
            {
                Toast.makeText(MainActivity.this, "Storage Permission Required", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if(wasActivityResumed)
        {
            //If the CAMERA2 is paused then resumed, it won't start again unless creating the whole camera again.
            if(usingFrontCamera)
            {
                createCameraSourceFront();
            }

            else
            {
                createCameraSourceBack();
            }
        }

    }

    @Override
    protected void onPause()
    {
        super.onPause();

        wasActivityResumed = true;
        stopCameraSource();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        stopCameraSource();

        if(previewFaceDetector != null)
        {
            previewFaceDetector.release();
        }
    }


    Camera2Source.IOverlayView overlayViewCallback = new Camera2Source.IOverlayView() {

        @Override
        public void setRect(final Rect rect) {

            runOnUiThread(new Runnable() {

                @Override
                public void run()
                {
                    mOverlayView.setRect(rect);
                    mOverlayView.requestLayout();
                }
            });
        }
    };
}