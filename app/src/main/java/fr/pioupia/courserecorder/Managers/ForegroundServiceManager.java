package fr.pioupia.courserecorder.Managers;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import fr.pioupia.courserecorder.BackgroundService;
import fr.pioupia.courserecorder.MainActivity;

public class ForegroundServiceManager {
    public static Timer timer = new Timer();
    public static boolean isServiceBounded = false;
    public static BackgroundService backgroundService;

    public static void restoreData(ActivityManager activityManager,
                                   Context context,
                                   Intent intent,
                                   MainActivity mainActivity,
                                   RelativeLayout statsContainer,
                                   RelativeLayout buttonContainer,
                                   RecyclerView tripsContainer,
                                   ImageView stopRecording,
                                   ImageView startRecording,
                                   ImageView resumeRecording,
                                   ImageView pauseRecording) {

        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (isIsServiceBounded()) {
                            getBackgroundService().setCallback((BackgroundService.ServiceCallback) context);

                            // restore data
                            mainActivity.isRecording = backgroundService.isRecording;
                            mainActivity.startPoint = backgroundService.startPoint;
                            mainActivity.startingTime = backgroundService.startingTime;
                            mainActivity.pauses = backgroundService.pauses;
                            IndexManager.setIndex(backgroundService.index);

                            // restore file streams
                            mainActivity.speeds = backgroundService.speeds;
                            mainActivity.alt = backgroundService.alt;
                            mainActivity.cords = backgroundService.cords;

                            mainActivity.runOnUiThread(() -> {
                                statsContainer.setVisibility(View.VISIBLE);
                                tripsContainer.setVisibility(View.GONE);
                                buttonContainer.setVisibility(View.VISIBLE);
                                stopRecording.setVisibility(View.VISIBLE);

                                startRecording.setVisibility(View.GONE);

                                if (!mainActivity.isRecording) {
                                    resumeRecording.setVisibility(View.VISIBLE);
                                    pauseRecording.setVisibility(View.GONE);
                                } else {
                                    resumeRecording.setVisibility(View.GONE);
                                    pauseRecording.setVisibility(View.VISIBLE);
                                }

                                String duration = DurationManager.getDurationFromStartingDate(mainActivity.startingTime);
                                mainActivity.durationView.setText("Durée d'enregistrement :" + duration);
                                mainActivity.altitudeView.setText("Altitude : " + (int) backgroundService.altMetric + "m");

                                mainActivity.speedView.setText(
                                        String.format(Locale.FRANCE, "Vitesse : %d km/h", (int) backgroundService.actualSpeed)
                                );

                                if (backgroundService.distance > 1000) {
                                    double d = (double) backgroundService.distance / 1000;
                                    mainActivity.distanceView.setText(
                                            String.format(Locale.FRANCE, "Distance parcourue : %.2f km", d)
                                    );
                                } else {
                                    mainActivity.distanceView.setText(
                                            String.format(Locale.FRANCE, "Distance parcourue : %d m", (int) backgroundService.distance)
                                    );
                                }
                            });

                            timer.cancel();
                            timer.purge();

                            timer = new Timer();
                        }
                    }
                }, 300, 100);
            }
        }
    }

    public static final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            BackgroundService.LocalBinder binder = (BackgroundService.LocalBinder) iBinder;
            setBackgroundService(
                    binder.getService()
            );

            setIsServiceBounded(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            setIsServiceBounded(false);
        }
    };


    public static boolean isIsServiceBounded() {
        return isServiceBounded;
    }

    public static void setIsServiceBounded(boolean isServiceBounded) {
        ForegroundServiceManager.isServiceBounded = isServiceBounded;
    }

    public static BackgroundService getBackgroundService() {
        return backgroundService;
    }

    public static void setBackgroundService(BackgroundService backgroundService) {
        ForegroundServiceManager.backgroundService = backgroundService;
    }
}
