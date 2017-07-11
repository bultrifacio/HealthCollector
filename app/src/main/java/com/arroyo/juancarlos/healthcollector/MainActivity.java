package com.arroyo.juancarlos.healthcollector;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class MainActivity extends BaseActivity {
    public static final String TAG = "SocialHealth";
    private GoogleApiClient googleUser = null;
    private long dailySteps = 0;
    private float dailyCalories = 0;
    private float dailyDream = 0;
    private float dailyDistance = 0;
    private float height = 0;
    private float weight = 0;
    private TextView userWeight;
    private TextView userHeight;
    private TextView userName;
    private TextView calories;
    private TextView distance;
    private TextView dream;
    private TextView steps;
    private ImageView userPhoto;
    private Handler handler;
    private DatabaseReference database;
    private String displayName;
    private String userNick;
    private String userEmail;
    private Uri profileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        database = FirebaseDatabase.getInstance().getReference();
        userName = (TextView) findViewById(R.id.userName);
        userHeight = (TextView) findViewById(R.id.userHeight);
        userWeight = (TextView) findViewById(R.id.userWeight);
        calories = (TextView) findViewById(R.id.caloriesView);
        distance = (TextView) findViewById(R.id.distanceView);
        dream = (TextView) findViewById(R.id.dreamView);
        steps = (TextView) findViewById(R.id.stepsView);
        userPhoto = (ImageView) findViewById(R.id.userPhoto);
        displayName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
        profileUri = FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl();
        for (UserInfo userInfo : FirebaseAuth.getInstance().getCurrentUser().getProviderData()) {
            if (displayName == null && userInfo.getDisplayName() != null) {
                displayName = userInfo.getDisplayName();
            }
            if (profileUri == null && userInfo.getPhotoUrl() != null) {
                profileUri = userInfo.getPhotoUrl();
            }
        }
        buildFitnessClient();
        RequestOptions options = new RequestOptions();
        options.circleCrop();
        Glide.with(getApplicationContext()).load(profileUri.toString())
                .apply(options)
                .into(userPhoto);
        handler = new Handler();
        handler.post(update);
        userName.setText(displayName);
        userWeight.setText("Peso: " + weight + " kg");
        userHeight.setText("Altura: " + height + " m");
        calories.setText(dailyCalories + " kcal");
        distance.setText(dailyDistance+ " km");
        steps.setText(dailySteps + "");
        dream.setText(dailyDream + " h");
        if (FirebaseAuth.getInstance() != null){
            userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            userNick = userEmail.substring(0, userEmail.indexOf("@"));
        }
    }

    private String getUsername() {
        return userNick;
    }

    private Runnable update = new Runnable() {
        @Override
        public void run() {
            readData();
            handler.postDelayed(this, 100);
        }
    };

    private void buildFitnessClient() {

        googleUser = new GoogleApiClient.Builder(this)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addScope(new Scope(Scopes.FITNESS_BODY_READ))
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(@Nullable Bundle bundle) {
                                Log.i(TAG, "Connected!!!!");
                            }
                            @Override
                            public void onConnectionSuspended(int i) {
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.w(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.w(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .enableAutoManage(this, 0, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.w(TAG, "Google Play services connection failed. Cause: " +
                                result.toString());
                        Snackbar.make(
                                MainActivity.this.findViewById(R.id.main_activity_view),
                                "Exception while connecting to Google Play services: " +
                                        result.getErrorMessage(),
                                Snackbar.LENGTH_INDEFINITE).show();
                    }
                })
                .build();
    }

    public static BigDecimal round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd;
    }

    private class VerifyDataTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            Calendar calendar = Calendar.getInstance();
            Date date = new Date();
            calendar.setTime(date);
            long endTime = calendar.getTimeInMillis();
            calendar.add(Calendar.YEAR, -1);
            long startTime = calendar.getTimeInMillis();
            DataReadRequest heightApi = new DataReadRequest.Builder()
                    .read(DataType.TYPE_HEIGHT)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .setLimit(1)
                    .build();
            DataReadRequest weightApi = new DataReadRequest.Builder()
                    .read(DataType.TYPE_WEIGHT)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .setLimit(1)
                    .build();

            DataReadResult totalResultHeight = Fitness.HistoryApi.readData(googleUser, heightApi)
                    .await(1, TimeUnit.HOURS);
            DataReadResult totalResultWeight = Fitness.HistoryApi.readData(googleUser, weightApi)
                    .await(1, TimeUnit.HOURS);
            PendingResult<DailyTotalResult> stepsApi = Fitness.HistoryApi.readDailyTotal(googleUser, DataType.TYPE_STEP_COUNT_DELTA);
            PendingResult<DailyTotalResult> caloriesApi = Fitness.HistoryApi.readDailyTotal(googleUser, DataType.TYPE_CALORIES_EXPENDED);
            DailyTotalResult totalResultSteps = stepsApi.await(1, TimeUnit.MINUTES);
            DailyTotalResult totalResultCalories = caloriesApi.await(1, TimeUnit.MINUTES);
            if (totalResultSteps.getStatus().isSuccess()) {
                DataSet totalSet = totalResultSteps.getTotal();
                dailySteps = totalSet.isEmpty()
                        ? 0
                        : totalSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();

            } else {
                Log.w(TAG, "There was a problem getting the distance count.");
            }
            if (totalResultCalories.getStatus().isSuccess()) {
                DataSet totalSet = totalResultCalories.getTotal();
                dailyCalories = totalSet.isEmpty()
                        ? 0
                        : (int) totalSet.getDataPoints().get(0).getValue(Field.FIELD_CALORIES).asFloat();

            } else {
                Log.w(TAG, "There was a problem getting the calories.");
            }

            if (totalResultHeight.getStatus().isSuccess()) {
                DataSet totalSet = totalResultHeight.getDataSet(DataType.TYPE_HEIGHT);
                height = totalSet.isEmpty()
                        ? 0
                        : totalSet.getDataPoints().get(0).getValue(Field.FIELD_HEIGHT).asFloat();
            } else {
                Log.w(TAG, "There was a problem getting the height.");
            }
            if (totalResultWeight.getStatus().isSuccess()) {
                DataSet totalSet = totalResultWeight.getDataSet(DataType.TYPE_WEIGHT);
                weight = totalSet.isEmpty()
                        ? 0
                        : totalSet.getDataPoints().get(0).getValue(Field.FIELD_WEIGHT).asFloat();
            } else {
                Log.w(TAG, "There was a problem getting the weight.");
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
            String currentDateandTime = sdf.format(new Date());
            Map<String, Object> dailyMap = new HashMap<>();
            dailyMap.put("/daily/" + currentDateandTime + "/" + getUsername() + "/pasos/", "" + dailySteps);
            dailyMap.put("/daily/" + currentDateandTime + "/" + getUsername() + "/calorías", "" + dailyCalories);
            dailyMap.put("/daily/" + currentDateandTime + "/" + getUsername() + "/sueño", "8.1");//MockData
            dailyMap.put("/daily/" + currentDateandTime + "/" + getUsername() + "/distancia", "7.1");//MockData
            database.updateChildren(dailyMap);

            Map<String, Object> profileMap = new HashMap<>();
            profileMap.put("/users/" + "/" + getUsername() + "/altura/", "" + height);
            profileMap.put("/users/" + "/" + getUsername() + "/email/", "" + userEmail);
            profileMap.put("/users/" + "/" + getUsername() + "/nombre/", "" + displayName);
            profileMap.put("/users/" + "/" + getUsername() + "/photoURL/", "" + profileUri.toString());
            profileMap.put("/users/" + "/" + getUsername() + "/peso/", "" + weight);
            database.updateChildren(profileMap);
            return null;
        }
    }

    private void readData() {
        new VerifyDataTask().execute();
        userWeight.setText("Peso: " + weight + " kg");
        userHeight.setText("Altura: " + height + " m");
        calories.setText(dailyCalories + " kcal");
        distance.setText(dailyDistance+ " km");
        steps.setText(dailySteps + "");
        dream.setText(dailyDream + " h");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the main; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, GoogleSignInActivity.class));
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}
