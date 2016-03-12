package se.bitcraze.crazyfliecontrol.controller;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import se.bitcraze.crazyfliecontrol2.MainActivity;

public class WearController extends AbstractController implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener {

    private final GoogleApiClient mApiClient;

    public WearController(Controls controls, MainActivity activity) {
        super(controls, activity);
        mApiClient = new GoogleApiClient.Builder(activity.getApplicationContext()).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
    }

    public void connect() {
        if (!mApiClient.isConnected() && !mApiClient.isConnecting()) {
           mApiClient.connect();
        }
    }

    public void disconnect() {
        Wearable.DataApi.removeListener(mApiClient, this);
        Wearable.MessageApi.removeListener(mApiClient, this);
        mApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (bundle != null) {
            Log.d(getClass().getName(), "onConnected bundle:" + bundle.toString());
        } else {
            Log.d(getClass().getName(), "onConnected bundle: null");
        }
        Wearable.DataApi.addListener(mApiClient, this);
        Wearable.MessageApi.addListener(mApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(getClass().getName(), "onConnectionSuspended i:" + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(getClass().getName(), "onConnectionResult result:" + connectionResult.toString());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo("/time") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    Toast.makeText(mActivity.getApplicationContext(), (System.currentTimeMillis() - dataMap.getLong("time")) + "ms", Toast.LENGTH_SHORT).show();
                    Log.d(getClass().getName(), "updating data took " + (System.currentTimeMillis() - dataMap.getLong("time")) + "ms");
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                // DataItem deleted
                Wearable.DataApi.deleteDataItems(mApiClient, event.getDataItem().getUri());
                Log.d(getClass().getName(), "data item deleted");
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        long millis = Long.valueOf(new String(messageEvent.getData()));
        Toast.makeText(mActivity.getApplicationContext(), (System.currentTimeMillis() - millis) + "ms", Toast.LENGTH_SHORT).show();
        Log.d(getClass().getName(), "updating data took " + (System.currentTimeMillis() - millis) + "ms");
    }
}
