package se.bitcraze.crazyfliecontrol2;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

import se.bitcraze.crazyfliecontrol.controller.Controls;
import se.bitcraze.crazyfliecontrol.controller.IController;
import se.bitcraze.crazyfliecontrol.prefs.PreferencesActivity;
import se.bitcraze.crazyflielib.BleLink;
import se.bitcraze.crazyflielib.crazyflie.ConnectionAdapter;
import se.bitcraze.crazyflielib.crazyflie.Crazyflie;
import se.bitcraze.crazyflielib.crazyradio.ConnectionData;
import se.bitcraze.crazyflielib.crazyradio.RadioDriver;
import se.bitcraze.crazyflielib.crtp.CommanderPacket;
import se.bitcraze.crazyflielib.crtp.CrtpDriver;

public class CommanderService extends Service {

    private static final String LOG_TAG = "CrazyflieControl";

    private Thread mSendJoystickDataThread;
    private Crazyflie mCrazyflie;
    private Controls mControls;
    private IController mController;
    private IBinder mBinder = new ServiceBinder();

    public CommanderService() {
        super();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    /**
     * Start thread to periodically send commands containing the user input
     */
    private void startSendJoystickDataThread() {
        mSendJoystickDataThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mCrazyflie != null) {
                    mCrazyflie.sendPacket(new CommanderPacket(mController.getRoll(), mController.getPitch(), mController.getYaw(), (char) (mController.getThrustAbsolute()), mControls.isXmode()));

                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Log.d(LOG_TAG, "SendJoystickDataThread was interrupted.");
                        break;
                    }
                }
            }
        });
        mSendJoystickDataThread.start();
    }

    public void connect() {
        Toast.makeText(getApplicationContext(),"connect()",Toast.LENGTH_SHORT).show();
        // ensure previous link is disconnected
        disconnect();

        CrtpDriver driver = null;

        if(MainActivity.isCrazyradioAvailable(getApplicationContext())) {
            try {
                driver = new RadioDriver(new UsbLinkAndroid(getApplicationContext()));
            } catch (IllegalArgumentException e) {
                Log.d(LOG_TAG, e.getMessage());
                //Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                //Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            //use BLE
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) &&
                    getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
                if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean(PreferencesActivity.KEY_PREF_BLATENCY_BOOL, false)) {
                    Log.d(LOG_TAG, "Using bluetooth write with response");
                    driver = new BleLink(getApplicationContext(), true);
                } else {
                    Log.d(LOG_TAG, "Using bluetooth write without response");
                    driver = new BleLink(getApplicationContext(), false);
                }
            } else {
                // TODO: improve error message
                Log.e(LOG_TAG, "No BLE support available.");
            }
        }

        if (driver != null) {

            // add listener for connection status
            driver.addConnectionListener(new ConnectionAdapter() {

                @Override
                public void connectionRequested(String connectionInfo) {
                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Connecting ...", Toast.LENGTH_SHORT).show();
                        }
                    });*/
                }

                @Override
                public void connected(String connectionInfo) {
                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                            if (mCrazyflie != null && mCrazyflie.getDriver() instanceof BleLink) {
                                mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button_connected_ble));
                            } else {
                                mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button_connected));
                            }
                        }
                    });
                    */
                    mCrazyflie.startConnectionSetup();
                }

                @Override
                public void setupFinished(String connectionInfo) {
                    startSendJoystickDataThread();
                }

                @Override
                public void connectionLost(String connectionInfo, final String msg) {
                    /*
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                            mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button));
                        }
                    });
                    */
                    disconnect();
                }

                @Override
                public void connectionFailed(String connectionInfo, final String msg) {
                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                        }
                    });
                    */
                    disconnect();
                }

                @Override
                public void disconnected(String connectionInfo) {
                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
                            mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button));
                        }
                    });
                    */
                }

                @Override
                public void linkQualityUpdated(final int quality) {
                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mFlightDataView.setLinkQualityText(quality + "%");
                        }
                    });
                    */
                }
            });

            mCrazyflie = new Crazyflie(driver);

            // connect
            // TODO refactor
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int radioChannel = Integer.parseInt(preferences.getString(PreferencesActivity.KEY_PREF_RADIO_CHANNEL, "10"));
            int radioDatarate = Integer.parseInt(preferences.getString(PreferencesActivity.KEY_PREF_RADIO_DATARATE, "0"));
            mCrazyflie.connect(new ConnectionData(radioChannel, radioDatarate));

//            mCrazyflie.addDataListener(new DataListener(CrtpPort.CONSOLE) {
//
//                @Override
//                public void dataReceived(CrtpPacket packet) {
//                    Log.d(LOG_TAG, "Received console packet: " + packet);
//                }
//
//            });
        } else {
            //Toast.makeText(this, "Cannot connect: Crazyradio not attached and Bluetooth LE not available", Toast.LENGTH_SHORT).show();
        }
    }

    public void disconnect() {
        Toast.makeText(getApplicationContext(),"connect()",Toast.LENGTH_SHORT).show();
        if (mCrazyflie != null) {
            mCrazyflie.disconnect();
            mCrazyflie = null;
        }
        if (mSendJoystickDataThread != null) {
            mSendJoystickDataThread.interrupt();
            mSendJoystickDataThread = null;
        }
    }

    public void setController(IController controller) {
        mController = controller;
    }

    public void setControls(Controls controls) {
        mControls = controls;
    }

    public class ServiceBinder extends Binder {
        CommanderService getService() {
            return CommanderService.this;
        }
    }
}
