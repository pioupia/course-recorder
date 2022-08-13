package fr.pioupia.courserecorder;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileOutputStream;
import java.util.Locale;

import fr.pioupia.courserecorder.Managers.DirectionManager;
import fr.pioupia.courserecorder.Managers.DurationManager;

public class BackgroundService extends Service {

    private final IBinder binder = new LocalBinder();

    /* Data */
    public double lastLatitude = 0;
    public double lastLongitude = 0;
    public float distance = 0;
    public double altMetric = 0;
    public double lastAlt = 0;
    public int speedCount = 1;
    public int speed = 0;
    public float maxSpeed = 0;

    private static final int LOCATION_REFRESH_TIME = 5 * 1000;
    private static final float LOCATION_REFRESH_DISTANCE = 0f;

    private boolean isCallbackDeclared = false;

    public LocationManager mLocationManager;

    ServiceCallback serviceCallback;

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String channelId = "CourseRecorder.237";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelId,
                    NotificationManager.IMPORTANCE_LOW
            );

            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification.Builder notification = new Notification.Builder(this, channelId)
                    .setContentText("The service is running")
                    .setContentTitle("Course Recorder")
                    .setSmallIcon(R.drawable.ic_launcher_background);

            startForeground(1001, notification.build());
        }

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Starting to get foreground location
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                LOCATION_REFRESH_TIME,
                LOCATION_REFRESH_DISTANCE,
                mLocationListener,
                Looper.getMainLooper());

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        BackgroundService getService() {
            // fetching data
            return BackgroundService.this;
        }
    }

    public void setEssentialData(ServiceCallback callback) {
        if (isCallbackDeclared) return;

        this.serviceCallback = callback;
        isCallbackDeclared = true;
    }

    public void stopListener() {
        mLocationManager.removeUpdates(mLocationListener);
    }

    @SuppressLint("MissingPermission")
    public void startListener() {
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                LOCATION_REFRESH_TIME,
                LOCATION_REFRESH_DISTANCE,
                mLocationListener,
                Looper.getMainLooper());
    }


    public LocationListener mLocationListener = new LocationListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onLocationChanged(final Location location) {
            if (!isCallbackDeclared) return;

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            double bearing = location.getBearing();
            double slope = 0;
            float actualSpeed = location.getSpeed();

            altMetric = location.getAltitude();
            speed += actualSpeed;
            speedCount++;

            if (maxSpeed < actualSpeed) {
                maxSpeed = actualSpeed;
            }

            if (speedCount > 2) {
                float [] dist = new float[2];

                Location.distanceBetween(lastLatitude, lastLongitude, latitude, longitude, dist);

                distance += dist[0];

                slope = 100 * (lastAlt - altMetric) / (dist[0]);

                if (slope > 100 || slope < -100) {
                    slope = 0;
                }
            }

            lastAlt = altMetric;
            lastLatitude = latitude;
            lastLongitude = longitude;

            serviceCallback.locationUpdated(location, bearing, slope, altMetric, actualSpeed, distance);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {

        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }
    };

    interface ServiceCallback {
        void locationUpdated(Location location, double bearing, double slope, double altMetric, float actualSpeed, float distance);
    }
}
