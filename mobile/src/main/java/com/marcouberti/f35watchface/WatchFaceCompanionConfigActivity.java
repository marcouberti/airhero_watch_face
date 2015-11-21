package com.marcouberti.f35watchface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.wearable.companion.WatchFaceCompanion;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.util.Date;

/**
 * The phone-side config activity for {@code DigitalWatchFaceService}. Like the watch-side config
 * activity ({@code DigitalWatchFaceWearableConfigActivity}), allows for setting the background
 * color. Additionally, enables setting the color for hour, minute and second digits.
 */
public class WatchFaceCompanionConfigActivity extends Activity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<DataApi.DataItemResult>,DataApi.DataListener {
    private static final String TAG = "IrisWatchFaceConfig";

    // TODO: use the shared constants (needs covering all the samples with Gradle build model)

    private GoogleApiClient mGoogleApiClient;
    private String mPeerId;

    private Toolbar toolbar;

    protected RecyclerView recyclerView;
    private GradientAdapter adapter;
    private RecyclerView.LayoutManager robotLayoutManager;
    CustomGradientView previewView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nature_gradient_watch_face_config);

        mPeerId = getIntent().getStringExtra(WatchFaceCompanion.EXTRA_PEER_ID);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        ComponentName name = getIntent().getParcelableExtra(
                WatchFaceCompanion.EXTRA_WATCH_FACE_COMPONENT);

        toolbar = (Toolbar)findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.back);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        /*
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.action_refresh) {
                    if(dashboardFragment != null) {
                        ((DashboardFragment)dashboardFragment).reloadRobotList(true);
                    }
                }
                return true;
            }
        });
        */

        previewView = (CustomGradientView)findViewById(R.id.gradient);

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);//no animation for changes
        recyclerView.setItemAnimator(animator);
        robotLayoutManager = new LinearLayoutManager(this);
        //robotLayoutManager = new GridLayoutManager(getActivity(),2);
        recyclerView.setLayoutManager(robotLayoutManager);
        adapter = new GradientAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected: " + connectionHint);

        if (mPeerId != null) {
            Uri.Builder builder = new Uri.Builder();
            Uri uri = builder.scheme("wear").path(WatchFaceUtil.PATH_WITH_FEATURE).authority(mPeerId).build();
            Wearable.DataApi.getDataItem(mGoogleApiClient, uri).setResultCallback(this);
            Wearable.DataApi.addListener(mGoogleApiClient, this);

        } else {
            displayNoConnectedDeviceDialog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    private void updateConfigDataItemAndUiOnStartup() {

        Log.d(TAG, "updateConfigDataItemAndUiOnStartup...");

        PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(mGoogleApiClient);
        results.setResultCallback(new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                if (dataItems.getCount() != 0) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItems.get(0));

                    // This should read the correct value.
                    int value = dataMapItem.getDataMap().getInt(WatchFaceUtil.KEY_BACKGROUND_COLOR);
                    updateUiForKey(WatchFaceUtil.KEY_BACKGROUND_COLOR, value);
                    Log.d(TAG, "aggiorno a startup background...");
                }

                dataItems.release();
            }
        });

        /*
        WatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                new WatchFaceUtil.FetchConfigDataMapCallback() {
                    @Override
                    public void onConfigDataMapFetched(DataMap startupConfig) {
                        // If the DataItem hasn't been created yet or some keys are missing,
                        // use the default values.
                        WatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                        updateUiForConfigDataMap(startupConfig);
                    }
                }
        );
        */
    }

    @Override // ResultCallback<DataApi.DataItemResult>
    public void onResult(DataApi.DataItemResult dataItemResult) {
        if (dataItemResult.getStatus().isSuccess() && dataItemResult.getDataItem() != null) {
            DataItem configDataItem = dataItemResult.getDataItem();
            DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
            DataMap config = dataMapItem.getDataMap();
            Log.d(TAG,"startup setup UI...");
            updateUiForConfigDataMap(config);
            //setUpAllPickers(config);
        } else {
            // If DataItem with the current config can't be retrieved, select the default items on
            // each picker.
            //setUpAllPickers(null);
        }
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
    }

    @Override // GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed: " + result);
    }

    private void displayNoConnectedDeviceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String messageText = getResources().getString(R.string.title_no_device_connected);
        String okText = getResources().getString(R.string.ok_no_device_connected);
        builder.setMessage(messageText)
                .setCancelable(false)
                .setPositiveButton(okText, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void sendConfigUpdateMessage(String configKey, int color) {
        if (mPeerId != null) {
            DataMap config = new DataMap();
            config.putLong(WatchFaceUtil.KEY_TIMESTAMP, new Date().getTime());
            config.putInt(configKey, color);
            byte[] rawData = config.toByteArray();
            Wearable.MessageApi.sendMessage(mGoogleApiClient, mPeerId, WatchFaceUtil.PATH_WITH_FEATURE, rawData);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sent watch face config message: " + configKey + " -> "
                        + Integer.toHexString(color));
            }
        }
    }

    @Override // DataApi.DataListener
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                continue;
            }

            DataItem dataItem = dataEvent.getDataItem();
            if (!dataItem.getUri().getPath().equals(WatchFaceUtil.PATH_WITH_FEATURE)) {
                continue;
            }

            DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
            DataMap config = dataMapItem.getDataMap();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Config DataItem updated:" + config);
            }
            updateUiForConfigDataMap(config);
        }
    }

    private void updateUiForConfigDataMap(final DataMap config) {
        boolean uiUpdated = false;
        for (String configKey : config.keySet()) {
            if (!config.containsKey(configKey)) {
                continue;
            }
            int color = config.getInt(configKey);
            Log.d(TAG, "Found watch face config key: " + configKey + " -> "
                        + color);

            if (updateUiForKey(configKey, color)) {
                uiUpdated = true;
            }
        }
    }

    /**
     * Updates the color of a UI item according to the given {@code configKey}. Does nothing if
     * {@code configKey} isn't recognized.
     *
     * @return whether UI has been updated
     */
    private boolean updateUiForKey(String configKey, final int color) {
        if (configKey.equals(WatchFaceUtil.KEY_BACKGROUND_COLOR)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    previewView.color = GradientsUtils.getGradients(getApplicationContext(), color);
                    previewView.invalidate();
                }
            });
        } else {
            Log.w(TAG, "Ignoring unknown config key: " + configKey);
            return false;
        }
        return true;
    }

    public void rateThisAppClick(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=com.marcouberti.f35watchface"));
        startActivity(intent);
    }

    public void seeOtherAppsClick(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://search?q=pub:Marco Uberti"));
        startActivity(intent);
    }

    public class GradientAdapter extends RecyclerView.Adapter<GradientAdapter.ViewHolder> {


        String[] gradients = getResources().getStringArray(R.array.gradients_face_array);

        // Provide a reference to the views for each data item
        // Complex data items may need more than one view per item, and
        // you provide access to all the views for a data item in a view holder
        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
            // each data item is just a string in this case
            public RelativeLayout itemContainer;
            public TextView nameText;
            public CustomGradientView gradientView;


            public ViewHolder(RelativeLayout v) {
                super(v);
                v.setOnClickListener(this);
                itemContainer = v;
                nameText = (TextView)v.findViewById(R.id.name);
                gradientView = (CustomGradientView)v.findViewById(R.id.gradient);
            }
            @Override
            public void onClick(View view) {
                    //position
                    int itemPosition = getAdapterPosition();
                    Log.d(TAG, "clicked position " + itemPosition);
                    String gradientName = gradients[itemPosition];
                    previewView.color = GradientsUtils.getGradients(getApplicationContext(),gradientName);
                    previewView.invalidate();
                    sendConfigUpdateMessage(WatchFaceUtil.KEY_BACKGROUND_COLOR, GradientsUtils.getColorID(gradientName));
            }
        }

        // Provide a suitable constructor (depends on the kind of dataset)
        public GradientAdapter() {}

        // Create new views (invoked by the layout manager)
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent,
                                                          int viewType) {

            // create a new view
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.gradient_item_list, parent, false);
            // set the view's size, margins, paddings and layout parameters

            GradientAdapter.ViewHolder vh = new GradientAdapter.ViewHolder((RelativeLayout) v);
            return vh;
        }


        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(GradientAdapter.ViewHolder vh, final int position) {

                GradientAdapter.ViewHolder holder = (GradientAdapter.ViewHolder)vh;
                //defaults
                holder.nameText.setText(gradients[position]);
                holder.gradientView.color = GradientsUtils.getGradients(getApplicationContext(),gradients[position]);
        }


        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return gradients.length;
        }

    }
}
