package com.parrot.sdksample.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.sdksample.R;
import com.parrot.sdksample.drone.MiniDrone;
import com.parrot.sdksample.view.H264VideoView;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Toast;


public class MiniDroneActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "MiniDroneActivity";
    private MiniDrone mMiniDrone;

    private ProgressDialog mConnectionProgressDialog;
    private ProgressDialog mDownloadProgressDialog;

    private H264VideoView mVideoView;

    private TextView mBatteryLabel;
    private Button mTakeOffLandBt;
    private Button mDownloadBt;

    private int mNbMaxDownload;
    private int mCurrentDownloadIndex;

    //Sensor related variables
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorRotationVector;

    //Sensor threshold values
    private float thresholdAccelerometerTakeOff;
    private float ValueX;
    private float ValueY;
    private float ValueZ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minidrone);

        initIHM();

        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);
        mMiniDrone = new MiniDrone(this, service);
        mMiniDrone.addListener(mMiniDroneListener);

        //Sensor Manager
        mSensorManager = (SensorManager) getSystemService(
                Context.SENSOR_SERVICE);

        //Getting the Accelerometer
        mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

    }

    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the minidrone is connecting
        if ((mMiniDrone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mMiniDrone.getConnectionState())))
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            // if the connection to the MiniDrone fails, finish the activity
            if (!mMiniDrone.connect()) {
                finish();
            }
        }

        if (mSensorAccelerometer != null) {
            mSensorManager.registerListener(this, mSensorAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mSensorRotationVector != null) {
            mSensorManager.registerListener(this, mSensorRotationVector,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

    }

    @Override
    public void onBackPressed() {
        if (mMiniDrone != null)
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            if (!mMiniDrone.disconnect()) {
                finish();
            }
        } else {
            finish();
        }
    }

    @Override
    public void onDestroy()
    {
        mMiniDrone.dispose();
        super.onDestroy();
    }

    private void initIHM() {
        mVideoView = (H264VideoView) findViewById(R.id.videoView);

        findViewById(R.id.emergencyBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.emergency();
            }
        });

        mTakeOffLandBt = (Button) findViewById(R.id.takeOffOrLandBt);
        mTakeOffLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (mMiniDrone.getFlyingState()) {
                    case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        mMiniDrone.takeOff();
                        break;
                    case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        mMiniDrone.land();
                        break;
                    default:
                }
            }
        });

        findViewById(R.id.takePictureBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.takePicture();
            }
        });

        mDownloadBt = (Button)findViewById(R.id.downloadBt);
        mDownloadBt.setEnabled(false);
        mDownloadBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.getLastFlightMedias();

                mDownloadProgressDialog = new ProgressDialog(MiniDroneActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(true);
                mDownloadProgressDialog.setMessage("Fetching medias");
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMiniDrone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
        });

        findViewById(R.id.gazUpBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setGaz((byte) 50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setGaz((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.gazDownBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setGaz((byte) -50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setGaz((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.yawLeftBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setYaw((byte) -50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setYaw((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.yawRightBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setYaw((byte) 50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setYaw((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.forwardBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setPitch((byte) 50);
                        mMiniDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setPitch((byte) 0);
                        mMiniDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.backBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setPitch((byte) -50);
                        mMiniDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setPitch((byte) 0);
                        mMiniDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.rollLeftBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setRoll((byte) -50);
                        mMiniDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setRoll((byte) 0);
                        mMiniDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.rollRightBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setRoll((byte) 50);
                        mMiniDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setRoll((byte) 0);
                        mMiniDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        mBatteryLabel = (TextView) findViewById(R.id.batteryLabel);
    }

    private final MiniDrone.Listener mMiniDroneListener = new MiniDrone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state)
            {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            mBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mTakeOffLandBt.setText("Take off");
                    mTakeOffLandBt.setEnabled(true);
                    mDownloadBt.setEnabled(true);
                    break;
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mTakeOffLandBt.setText("Land");
                    mTakeOffLandBt.setEnabled(true);
                    mDownloadBt.setEnabled(false);
                    break;
                default:
                    mTakeOffLandBt.setEnabled(false);
                    mDownloadBt.setEnabled(false);
            }
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            mVideoView.configureDecoder(codec);
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            mVideoView.displayFrame(frame);
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            mDownloadProgressDialog.dismiss();

            mNbMaxDownload = nbMedias;
            mCurrentDownloadIndex = 1;

            if (nbMedias > 0) {
                mDownloadProgressDialog = new ProgressDialog(MiniDroneActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(false);
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setMessage("Downloading medias");
                mDownloadProgressDialog.setMax(mNbMaxDownload * 100);
                mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);
                mDownloadProgressDialog.setProgress(0);
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMiniDrone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            mDownloadProgressDialog.setProgress(((mCurrentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            mCurrentDownloadIndex++;
            mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);

            if (mCurrentDownloadIndex > mNbMaxDownload) {
                mDownloadProgressDialog.dismiss();
                mDownloadProgressDialog = null;
            }
        }
    };

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        // The sensor type (as defined in the Sensor class).
        int sensorType = sensorEvent.sensor.getType();

        // The new data value of the sensor.  Both the light and proximity
        // sensors report one value at a time, which is always the first
        // element in the values array.
        float currentValueX = sensorEvent.values[0];
        float currentValueY = sensorEvent.values[1];
        float currentValueZ = sensorEvent.values[2];



        switch (sensorType){
            case Sensor.TYPE_ACCELEROMETER:
                ValueX = currentValueX;
                ValueY = currentValueY;
                if(ValueX < -10.0 && mTakeOffLandBt.getText() == "Take off"){
                    mMiniDrone.takeOff();
                    ValueX= 0 ;
                }
                else if(ValueX < -10.0 &&  mTakeOffLandBt.getText() == "Land"){
                    mMiniDrone.land();
                    ValueX = 0;
                }


                //Tilt Right
                if (ValueY > 6.0 && ValueX >-4.0 && ValueX < 8.0){
                    mMiniDrone.setRoll((byte) 50);
                    mMiniDrone.setPitch((byte)0);
                    mMiniDrone.setFlag((byte) 1);
                    //Toast.makeText(MiniDroneActivity.this, "Tilt Right", Toast.LENGTH_SHORT).show();
                    //ValueY = 0;
                }

                //Tilt left
                else if(ValueY < -6.0 && ValueX >-4.0 && ValueX < 8.0){
                    mMiniDrone.setRoll((byte) -50);
                    mMiniDrone.setPitch((byte)0);
                    mMiniDrone.setFlag((byte) 1);
                    //Toast.makeText(MiniDroneActivity.this, "Tilt Left", Toast.LENGTH_SHORT).show();
                    //ValueY = 0;
                }

//                else if(ValueY >-6.0 && ValueY < 6.0){
//                    mMiniDrone.setRoll((byte) 0);
//                    mMiniDrone.setFlag((byte) 0);
//                    //ValueX = 0;
//                }


                //Backward
                else if (ValueX > 8.0 && ValueY >-6.0 && ValueY < 6.0){
                    mMiniDrone.setPitch((byte) -50);
                    mMiniDrone.setRoll((byte) 0);
                    mMiniDrone.setFlag((byte) 1);
                    //Toast.makeText(MiniDroneActivity.this, "Tilt Right", Toast.LENGTH_SHORT).show();
                    //ValueY = 0;
                }

                //Forward
                else if(ValueX < -4.0 && ValueX > -10.0  && ValueY >-6.0 && ValueY < 6.0){
                    mMiniDrone.setPitch((byte) 50);
                    mMiniDrone.setRoll((byte) 0);
                    mMiniDrone.setFlag((byte) 1);
                    //Toast.makeText(MiniDroneActivity.this, "Tilt Left", Toast.LENGTH_SHORT).show();
                    //ValueY = 0;
                }

                //forward right
                else if(ValueX < -4.0 && ValueX > -10.0 && ValueY > 6.0  ) {
                    mMiniDrone.setPitch((byte) 50);
                    mMiniDrone.setRoll((byte) 50);
                    mMiniDrone.setFlag((byte) 1);
                }

                //forward left
                else if(ValueX < -4.0 && ValueX > -10.0 && ValueY < -6.0  ) {
                    mMiniDrone.setPitch((byte) 50);
                    mMiniDrone.setRoll((byte) -50);
                    mMiniDrone.setFlag((byte) 1);
                }

                //backward right
                else if (ValueX > 8.0 && ValueY > 6.0) {
                    mMiniDrone.setPitch((byte) -50);
                    mMiniDrone.setRoll((byte) 50);
                    mMiniDrone.setFlag((byte) 1);
                }

                //backward left
                else if (ValueX > 8.0 && ValueY < -6.0){
                    mMiniDrone.setPitch((byte) -50);
                    mMiniDrone.setRoll((byte) -50);
                    mMiniDrone.setFlag((byte) 1);
                }

                else if(ValueX >-4.0 && ValueX < 8.0 && ValueY >-6.0 && ValueY < 6.0){
                    mMiniDrone.setPitch((byte) 0);
                    mMiniDrone.setRoll((byte)0);
                    mMiniDrone.setFlag((byte) 0);
                    //ValueX = 0;
                }

                break;

//            case Sensor.TYPE_ROTATION_VECTOR:
//                ValueX = currentValueX;
//                if (ValueX > 0.30){
//                    mMiniDrone.setRoll((byte) -50);
//                    mMiniDrone.setFlag((byte) 1);
//                    Toast.makeText(MiniDroneActivity.this, "Tilt Right", Toast.LENGTH_SHORT).show();
//                    ValueX = 0;
//                }
//
//                if(ValueX >-0.30 && ValueX < 0.30){
//                    mMiniDrone.setRoll((byte) 0);
//                    mMiniDrone.setFlag((byte) 0);
//                    ValueX = 0;
//                }
//
//                if(ValueX < -0.20){
//                    mMiniDrone.setRoll((byte) 50);
//                    mMiniDrone.setFlag((byte) 1);
//                    Toast.makeText(MiniDroneActivity.this, "Tilt Left", Toast.LENGTH_SHORT).show();
//                    ValueX = 0;
//                }
//
//                break;

        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public boolean shake(){

        return false;

    }
}

