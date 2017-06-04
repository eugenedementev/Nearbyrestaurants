package me.eugenedementev.nearbyrestaurants;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnMarkerClickListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, GoogleMap.OnInfoWindowClickListener {

    //TODO make Fragment for map.

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private GoogleMap mMap;
    private String mYelpAccessToken;
    private GoogleApiClient mGoogleApiClient;
    private boolean mLocationPermissionGranted;
    private Location mLastKnownLocation;
    private float DEFAULT_ZOOM = 15;
    private LatLng mDefaultLocation = new LatLng(-34,115);

    public static final String RESTAURANT_INTENT_TAG = "rest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        if (mGoogleApiClient == null){
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .enableAutoManage(this,this)
                    .addConnectionCallbacks(this)
                    .addApi(LocationServices.API)
                    .addApi(Places.GEO_DATA_API)
                    .addApi(Places.PLACE_DETECTION_API)
                    .build();
        }
        if (!mGoogleApiClient.isConnected())
            mGoogleApiClient.connect();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mGoogleApiClient.disconnect();
        super.onPause();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnCameraIdleListener(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnInfoWindowClickListener(this);
        updateLocationUI();
        getDeviceLocation();
    }

    private void updateLocationUI() {
        if (mMap == null){
            return;
        }
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        if (mLocationPermissionGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            mMap.setMyLocationEnabled(false);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            mLastKnownLocation = null;
        }
    }

    private String getRestaurantList(double latitude, double longtitue,double radius){
        Log.d(LOG_TAG, "Try to get data from yelp API. lat: "+ latitude + "|long: " + longtitue + "|radius: " + radius);

        if (radius > 40000){ //maximum radius value in Yelp API
            return null;
        }
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String resultJsonStr = null;


        try {
            final String FORECAST_BASE_URL =
                    "https://api.yelp.com/v3/businesses/search?";
            final String LATITUDE_PARAM = "latitude";
            final String LONGTITUDE_PARAM = "longitude";
            final String TERM_PARAM = "term";
            final String LIMIT_PARAM = "limit";
            //Optional. Search radius in meters. If the value is too large, a AREA_TOO_LARGE error may be returned. The max value is 40000 meters (25 miles).
            final String RADIUS_PARAM = "radius";

            String limit = "50";
            String latitudeString = String.valueOf(latitude);
            String longtitudeString = String.valueOf(longtitue);

            Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(RADIUS_PARAM, String.valueOf((int)radius))
                    .appendQueryParameter(LATITUDE_PARAM,latitudeString)
                    .appendQueryParameter(LONGTITUDE_PARAM,longtitudeString)
                    .appendQueryParameter(TERM_PARAM,"restaurant")
                    .appendQueryParameter(LIMIT_PARAM,limit)
                    .build();

            URL url = new URL(builtUri.toString());

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.addRequestProperty("Authorization","Bearer "+mYelpAccessToken);
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                return null;
            }
            resultJsonStr = buffer.toString();
            Log.d(LOG_TAG,"List of restaurants: "+resultJsonStr);
            return resultJsonStr;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }
    }

    private void getYelpAccessToken(){
        Log.d(LOG_TAG, "Starting obtaining Yelp Access token.");

        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        String resultJsonStr = null;
        try {
            final String YELP_GET_TOKEN_BASE_URL =
                    "https://api.yelp.com/oauth2/token?";
            final String GRANT_TYPE_PARAM = "grant_type";
            final String CLIENT_ID_PARAM = "client_id";
            final String CLIENT_SECRET_PARAM = "client_secret";

            Uri builtUri = Uri.parse(YELP_GET_TOKEN_BASE_URL).buildUpon()
                    .appendQueryParameter(GRANT_TYPE_PARAM, BuildConfig.GRANT_TYPE)
                    .appendQueryParameter(CLIENT_ID_PARAM, BuildConfig.CLIENT_ID)
                    .appendQueryParameter(CLIENT_SECRET_PARAM, BuildConfig.CLIENT_SECRET)
                    .build();

            URL url = new URL(builtUri.toString());

            // Create the request to YelpAPI, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            urlConnection.setRequestMethod("POST");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();

            if (inputStream == null) {
                return;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                return;
            }

            resultJsonStr = buffer.toString();
            Log.d(LOG_TAG,resultJsonStr);

            //Get token from JSON string
            mYelpAccessToken = getTokenFromJson(resultJsonStr);
            Log.d(LOG_TAG,mYelpAccessToken);

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            return ;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
        reader.close();
    } catch (final IOException e) {
        Log.e(LOG_TAG, "Error closing stream", e);
    }
}
        }
                }

    @Override
    public void onCameraIdle() {
        Log.d(LOG_TAG,"Camera is idle now");
        updateMap();
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (!marker.isInfoWindowShown()){
            marker.showInfoWindow();
        }else {
            marker.hideInfoWindow();
        }
        return true;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private void getDeviceLocation(){
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

        }
        if (mLocationPermissionGranted) {
            mLastKnownLocation = LocationServices.FusedLocationApi
                    .getLastLocation(mGoogleApiClient);
        }
        // Set the map's camera position to the current location of the device.

       if (mLastKnownLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(mLastKnownLocation.getLatitude(),
                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
       } else {
            Log.d(LOG_TAG, "Current location is null. Using defaults.");
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
       }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode){
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:{
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Intent intent = new Intent(this, RestaurantDetailActivity.class);
        intent.putExtra(RESTAURANT_INTENT_TAG, (String) marker.getTag());
        startActivity(intent);
    }

    public class FetchYelpData extends AsyncTask<Double, Object, String> {

    private final String LOG_TAG = FetchYelpData.class.getSimpleName();

    @Override
    protected String doInBackground(Double... params) {
        if (params.length == 0){
            return null;
        }
        if (mYelpAccessToken == null){
            getYelpAccessToken();
        }
        String restaurantList = getRestaurantList(params[0],params[1],params[2]);
        return restaurantList;
    }

        @Override
        protected void onPostExecute(String result) {
            final String YAPI_BUSINESSES = "businesses";
            final String YAPI_COORDINATES = "coordinates";
            final String YAPI_LAT = "latitude";
            final String YAPI_LONG = "longitude";
            final String YAPI_NAME = "name";
            final String YAPI_RATING = "rating";
            final String YAPI_LOCATION = "location";
            final String YAPI_ADDRESS1 = "address1";

            mMap.clear();

            try {
                JSONObject resultObject = new JSONObject(result);
                JSONArray restaurantsArray = resultObject.getJSONArray(YAPI_BUSINESSES);
                for (int i=0; i < restaurantsArray.length();i++){
                    JSONObject restaurant = restaurantsArray.getJSONObject(i);
                    JSONObject restaurantJSONCoordinates = restaurant.getJSONObject(YAPI_COORDINATES);
                    LatLng restaurantCoordinates = new LatLng(
                            restaurantJSONCoordinates.getDouble(YAPI_LAT),
                            restaurantJSONCoordinates.getDouble(YAPI_LONG)
                    );
                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(restaurantCoordinates)
                            .title(restaurant.getString(YAPI_NAME))
                            .snippet(
                                    "Rating: " +
                                    restaurant.getString(YAPI_RATING) +
                                    " Address: " +
                                    restaurant.getJSONObject(YAPI_LOCATION).getString(YAPI_ADDRESS1)
                            )
                    );
                    marker.setTag(restaurant.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private String getTokenFromJson(String jsonString){
        final String TOKEN_RESPONSE = "access_token";

        try {
            JSONObject responseObject = new JSONObject(jsonString);
            return responseObject.getString(TOKEN_RESPONSE);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void updateMap(){
        VisibleRegion visibleRegion = mMap.getProjection().getVisibleRegion();
        CameraPosition cameraPosition = mMap.getCameraPosition();
        double radiusOfOuterCircle = getVisibleRadius(
                visibleRegion.farLeft.longitude,
                visibleRegion.farLeft.latitude,
                cameraPosition.target.longitude,
                cameraPosition.target.latitude
        );

        if (radiusOfOuterCircle > 40000){ //If radius of visible zone more than 40 000 meters show zoom marker
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(mMap.getCameraPosition().target).title("Area is too big").snippet("Zoom in the map"));
        }else{ //if radius less than 40 000 meters, show restaurants
            FetchYelpData fetchingTask = new FetchYelpData();
            fetchingTask.execute(cameraPosition.target.latitude,cameraPosition.target.longitude,radiusOfOuterCircle);
        }
    }

    private double getVisibleRadius(double long1, double lat1, double long2, double lat2){
        double earthRadius = 6371000; //meters
        double dLat = Math.toRadians(lat2-lat1);
        double dLng = Math.toRadians(long2-long1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng/2) * Math.sin(dLng/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return earthRadius * c;
    }

}
