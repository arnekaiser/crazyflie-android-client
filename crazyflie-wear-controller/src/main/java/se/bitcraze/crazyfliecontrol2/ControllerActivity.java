package se.bitcraze.crazyfliecontrol2;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

public class ControllerActivity extends WearableActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mApiClient;
    private Timer mTimer;
    private Uri mDataUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        mApiClient = new GoogleApiClient.Builder(getApplicationContext()).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        mTimer = new Timer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTimer.cancel();
        if (mDataUri != null)
            Wearable.DataApi.deleteDataItems(mApiClient, mDataUri);
        mDataUri = null;
        mApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (bundle != null) {
            Log.d(getClass().getName(), "onConnected bundle:" + bundle.toString());
        } else {
            Log.d(getClass().getName(), "onConnected bundle: null");
        }

        mTimer = new Timer();
        //mTimer.schedule(new SendDataTask(), 1000, 20);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(getClass().getName(), "onConnectionSuspended i:" + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(getClass().getName(), "onConnectionResult result:" + connectionResult.toString());
    }

    private void sendTime() {
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/time");
        long timeMillis = System.currentTimeMillis();
        putDataMapReq.getDataMap().putLong("time", timeMillis);
        putDataMapReq.setUrgent();

        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        if (mDataUri == null) {
            mDataUri = putDataReq.getUri();
        }
        putDataMapReq.setUrgent();

        Wearable.DataApi.putDataItem(mApiClient, putDataReq);
    }

    private void sendTimeMessage(String node) {
        Wearable.MessageApi.sendMessage(mApiClient, node, "time", String.format("%d", System.currentTimeMillis()).getBytes());
    }

    public void onSendButtonClicked(View v) {
        new SendTimeMessageTask().execute();
    }


    private class SendDataTask extends TimerTask {

        @Override
        public void run() {
            sendTime();
        }

    }

    public class SendTimeMessageTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            final Collection<String> nodes = getNodes();
            for (final String node : nodes) {
                sendTimeMessage(node);
            }
            return null;
        }

        private Collection<String> getNodes() {
            final HashSet<String> results = new HashSet<String>();
            final NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await();

            for (final Node node : nodes.getNodes()) {
                results.add(node.getId());
            }

            return results;
        }
    }

}
