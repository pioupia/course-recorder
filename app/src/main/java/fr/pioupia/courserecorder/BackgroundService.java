package fr.pioupia.courserecorder;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import fr.pioupia.courserecorder.Managers.DurationManager;
import fr.pioupia.courserecorder.Managers.IndexManager;

public class BackgroundService extends Service {

    private final IBinder binder = new LocalBinder();

    /* General Data */
    public boolean isRecording = true;
    public double[] startPoint = new double[2];
    public long startingTime = 0;
    public Array pauses = new Array(1);
    public int index = 0;

    /* Files stream */
    public FileOutputStream speeds = null;
    public FileOutputStream cords = null;
    public FileOutputStream alt = null;

    /* Data */
    public double lastLatitude = 0;
    public double lastLongitude = 0;
    public float distance = 0;
    public double altMetric = 0;
    public double lastAlt = 0;
    public int speedCount = 1;
    public int speed = 0;
    public float actualSpeed = 0;
    public float maxSpeed = 0;

    private static final int LOCATION_REFRESH_TIME = 5 * 1000;
    private static final float LOCATION_REFRESH_DISTANCE = 0f;

    private boolean isCallbackDeclared = false;

    public LocationManager mLocationManager;

    ServiceCallback serviceCallback;

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotification(
                String.format(Locale.ENGLISH,
                        "%s\n%s %s\n%s %s\n%s %.2fkm/h\n%s %.2fkm/h",
                        getString(R.string.trip_recording),
                        getString(R.string.duration),
                        "0min",
                        getString(R.string.distance),
                        "0m",
                        getString(R.string.speed_average),
                        0.0,
                        getString(R.string.max_speed),
                        0.0
                )
        );

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Starting to get foreground location
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                LOCATION_REFRESH_TIME,
                LOCATION_REFRESH_DISTANCE,
                mLocationListener,
                Looper.getMainLooper());

        return super.onStartCommand(intent, flags, startId);
    }

    public void createNotification(String contentText) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String channelId = "CourseRecorder.237";

            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelId,
                    NotificationManager.IMPORTANCE_LOW
            );

            getSystemService(NotificationManager.class).createNotificationChannel(channel);

            Intent myIntent = new Intent(getApplicationContext(), MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    getApplicationContext(),
                    1,
                    myIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            Notification notification = new Notification.Builder(this, channelId)
                    .setContentText(contentText)
                    .setContentTitle("Course Recorder")
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(contentText))
                    .setContentIntent(pendingIntent)
                    .build();

            startForeground(1001, notification);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        public BackgroundService getService() {
            // fetching data
            return BackgroundService.this;
        }
    }

    public void setCallback(ServiceCallback callback) {
        this.serviceCallback = callback;
        isCallbackDeclared = true;
    }

    public void setEssentialData(ServiceCallback callback, FileOutputStream speeds, FileOutputStream cords, FileOutputStream alt) {
        if (isCallbackDeclared) return;

        this.serviceCallback = callback;
        isCallbackDeclared = true;

        this.speeds = speeds;
        this.cords = cords;
        this.alt = alt;
        this.index = IndexManager.getIndex();
    }

    public void setPauses(Array pauses) {
        this.pauses = pauses;
    }

    public void stopListener() {
        mLocationManager.removeUpdates(mLocationListener);
        isRecording = false;
    }

    @SuppressLint("MissingPermission")
    public void startListener() {
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                LOCATION_REFRESH_TIME,
                LOCATION_REFRESH_DISTANCE,
                mLocationListener,
                Looper.getMainLooper());
        isRecording = true;
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
            actualSpeed = location.getSpeed();

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

                slope = 100 * (altMetric - lastAlt) / (dist[0]);

                if (slope > 100 || slope < -100) {
                    slope = 0;
                }
            } else {
                startPoint[0] = longitude;
                startPoint[1] = latitude;

                Date date = new Date();
                startingTime = date.getTime();
            }

            lastAlt = altMetric;
            lastLatitude = latitude;
            lastLongitude = longitude;

            try {
                BackgroundService.this.speeds.write(
                        String.format(Locale.ENGLISH, "%.2f ", actualSpeed).getBytes(StandardCharsets.UTF_8)
                );

                BackgroundService.this.cords.write(
                        String.format(Locale.ENGLISH, "%f %f  ", longitude, latitude).getBytes(StandardCharsets.UTF_8)
                );

                BackgroundService.this.alt.write(
                        String.format(Locale.ENGLISH, "%.2f ", lastAlt).getBytes(StandardCharsets.UTF_8)
                );
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (speedCount % 2 == 0) {
                createNotification(
                        String.format(Locale.ENGLISH,
                                "%s\n%s %s\n%s %.2fkm\n%s %.2fkm/h\n%s %.2fkm/h",
                                getString(R.string.trip_recording),
                                getString(R.string.duration),
                                DurationManager.getDurationFromStartingDate(startingTime),
                                getString(R.string.distance),
                                distance / 1000,
                                getString(R.string.speed_average),
                                ((float) speed / speedCount) * 36 / 10,
                                getString(R.string.max_speed),
                                maxSpeed * 36 / 10
                        )
                );
            }

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

    public interface ServiceCallback {
        void locationUpdated(Location location, double bearing, double slope, double altMetric, float actualSpeed, float distance);
    }
}
