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

import java.util.Locale;

public class BackgroundService extends Service {

    private final IBinder binder = new LocalBinder();

    private static final int LOCATION_REFRESH_TIME = 5 * 1000;
    private static final float LOCATION_REFRESH_DISTANCE = 0f;

    private boolean isCallbackDeclared = false;

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


        // Starting to get foreground location
        LocationManager mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
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

    public void setCallback(ServiceCallback callback) {
        if (isCallbackDeclared) return;

        this.serviceCallback = callback;
        isCallbackDeclared = true;
    }


    public LocationListener mLocationListener = new LocationListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onLocationChanged(final Location location) {
            Log.d("GPS", "localisation : " + location.toString());
            String coordonnees = String.format(Locale.FRANCE, "Latitude : %f - Longitude : %f\n", location.getLatitude(), location.getLongitude());
            Log.d("GPS", "coordonnees : " + coordonnees);

            if (!isCallbackDeclared) return;

            serviceCallback.locationUpdated(location);
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
        void locationUpdated(Location location);
    }
}
