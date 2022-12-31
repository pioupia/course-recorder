package fr.pioupia.courserecorder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import fr.pioupia.courserecorder.Activites.DetailsTripsActivity;
import fr.pioupia.courserecorder.Adapters.TripsList.RecyclerViewAdapter;
import fr.pioupia.courserecorder.Adapters.TripsList.RecyclerViewInterface;
import fr.pioupia.courserecorder.Managers.DirectionManager;
import fr.pioupia.courserecorder.Managers.DurationManager;
import fr.pioupia.courserecorder.Managers.PermissionsManager;
import fr.pioupia.courserecorder.Models.TripData;


public class MainActivity extends AppCompatActivity implements BackgroundService.ServiceCallback, RecyclerViewInterface {
    public int index = 0;

    public Timer timer = new Timer();

    /* Files stream */
    public FileOutputStream speeds = null;
    public FileOutputStream cords = null;
    public FileOutputStream alt = null;

    /* Data in real time */
    public boolean isRecording = false;
    public double[] startPoint = new double[2];
    public long startingTime = 0;
    public Array pauses = new Array(1);

    public boolean havePermissions = false;
    public String rootDir = "";

    public boolean isServiceBounded = false;
    public BackgroundService backgroundService;

    /* View */
    public TextView durationView = null;
    public TextView distanceView = null;
    public TextView speedView = null;
    public TextView directionView = null;
    public TextView altitudeView = null;
    public TextView penteView = null;

    /* Background app */
    PermissionsManager permissions = new PermissionsManager();

    public ArrayList<TripData> lastTrips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(MainActivity.this, BackgroundService.class);

        rootDir = getApplicationInfo().dataDir + "/records";

        RecyclerView tripsContainer = findViewById(R.id.tripsContainer);
        RelativeLayout statsContainer = findViewById(R.id.statsContainer);
        RelativeLayout buttonContainer = findViewById(R.id.buttonsContainer);
        ImageView startRecording = findViewById(R.id.startRecording);
        ImageView stopRecording = findViewById(R.id.stopRecording);
        ImageView pauseRecording = findViewById(R.id.pauseRecording);
        ImageView resumeRecording = findViewById(R.id.resumeRecording);
        durationView = findViewById(R.id.duration);
        distanceView = findViewById(R.id.distance);
        speedView = findViewById(R.id.speed);
        directionView = findViewById(R.id.direction);
        altitudeView = findViewById(R.id.altitude);
        penteView = findViewById(R.id.slop);

        if (this.foregroundServiceRunning()) {
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (isServiceBounded) {
                                backgroundService.setCallback(MainActivity.this);

                                // restore data
                                isRecording = backgroundService.isRecording;
                                startPoint = backgroundService.startPoint;
                                startingTime = backgroundService.startingTime;
                                pauses = backgroundService.pauses;
                                index = backgroundService.index;

                                MainActivity.this.runOnUiThread(() -> {
                                    statsContainer.setVisibility(View.VISIBLE);
                                    tripsContainer.setVisibility(View.GONE);
                                    buttonContainer.setVisibility(View.VISIBLE);
                                    stopRecording.setVisibility(View.VISIBLE);

                                    startRecording.setVisibility(View.GONE);

                                    if (!isRecording) {
                                        resumeRecording.setVisibility(View.VISIBLE);
                                        pauseRecording.setVisibility(View.GONE);
                                    } else {
                                        resumeRecording.setVisibility(View.GONE);
                                        pauseRecording.setVisibility(View.VISIBLE);
                                    }

                                    String duration = new DurationManager().getDurationFromStartingDate(startingTime);
                                    durationView.setText("Durée d'enregistrement :" + duration);
                                    altitudeView.setText("Altitude : " + (int) backgroundService.altMetric + "m");

                                    speedView.setText(
                                            String.format(Locale.FRANCE, "Vitesse : %d km/h", (int) backgroundService.actualSpeed)
                                    );

                                    if (backgroundService.distance > 1000) {
                                        double d = (double) backgroundService.distance / 1000;
                                        distanceView.setText(
                                                String.format(Locale.FRANCE, "Distance parcourue : %.2f km", d)
                                        );
                                    } else {
                                        distanceView.setText(
                                                String.format(Locale.FRANCE, "Distance parcourue : %d m", (int) backgroundService.distance)
                                        );
                                    }
                                });

                                timer.cancel();
                                timer.purge();
                            }
                        }
                    }, 300, 100);
                }
            }
        } else {
            File file = new File(rootDir);

            if (!file.exists()) {
                file.mkdir();
            }

            file = new File(rootDir + "/index");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                    if (file.canWrite()) {
                        FileOutputStream outputStream = new FileOutputStream(rootDir + "/index");

                        outputStream.write((byte) index);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    RandomAccessFile f = new RandomAccessFile(file, "r");
                    byte[] b = new byte[(int) f.length()];
                    f.readFully(b);

                    index = b[0];
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            setupLastTripData();

            havePermissions = this.permissions.verifyPermissions(MainActivity.this);

            if (!havePermissions) {
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        1
                );

                havePermissions = this.permissions.verifyPermissions(MainActivity.this);
            }
        }


        startRecording.setOnClickListener(v -> {
            havePermissions = permissions.askPermissions(MainActivity.this, MainActivity.this, havePermissions);
            if (!havePermissions) return;

            if (permissions.locationEnabled(MainActivity.this)) return;

            if (!this.foregroundServiceRunning()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }

            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

            v.setVisibility(View.GONE);
            buttonContainer.setVisibility(View.VISIBLE);

            statsContainer.setVisibility(View.VISIBLE);
            tripsContainer.setVisibility(View.GONE);

            Date date = new Date();

            startingTime = date.getTime();

            File file1 = new File(rootDir + "/_temp/" + index);

            if (!file1.exists()) {
                file1.mkdirs();
            }

            try {
                String tempDir = rootDir + "/_temp/" + index + "/";
                speeds = new FileOutputStream(tempDir + "speeds");
                cords = new FileOutputStream(tempDir + "cords");
                alt = new FileOutputStream(tempDir + "alt");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            isRecording = true;

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (isServiceBounded) {
                        backgroundService.setEssentialData(MainActivity.this, speeds, cords, alt, index);
                        timer.cancel();
                        timer.purge();
                    }
                }
            }, 300, 100);
        });

        stopRecording.setOnClickListener(view -> {
            long endTime = new Date().getTime();
            startPoint = backgroundService.startPoint;

            backgroundService.stopListener();

            // Reset states
            buttonContainer.setVisibility(View.GONE);
            resumeRecording.setVisibility(View.GONE);

            startRecording.setVisibility(View.VISIBLE);
            pauseRecording.setVisibility(View.VISIBLE);

            statsContainer.setVisibility(View.GONE);
            tripsContainer.setVisibility(View.VISIBLE);

            isRecording = false;
            if (speeds != null) {
                try {
                    speeds.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                speeds = null;
            }

            if (cords != null) {
                try {
                    cords.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                cords = null;
            }

            if (alt != null) {
                try {
                    alt.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                alt = null;
            }

            // If the pause is not end by the user because he shut down the record
            if (pauses.count % 2 != 0) {
                long date = new DurationManager().getMSDuration(pauses.get(pauses.count - 1), endTime);
                pauses.push(date);
            }

            float distance = backgroundService.distance;
            int speedCount = backgroundService.speedCount;
            double speed = backgroundService.speed;
            double maxSpeed = backgroundService.maxSpeed;
            double lastLatitude = backgroundService.lastLatitude;
            double lastLongitude = backgroundService.lastLongitude;

            try {
                FileOutputStream endingData = new FileOutputStream(rootDir + "/_temp/" + index + "/infos");
                endingData.write(String.valueOf(startingTime).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');
                endingData.write(String.valueOf(endTime).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');

                int duration = (int) ((new DurationManager().getPathDuration(startingTime, endTime, pauses.toArray())) * 1e-3);
                endingData.write(String.valueOf(duration).getBytes(StandardCharsets.UTF_8));

                endingData.write(' ');
                endingData.write(String.valueOf(distance).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');

                float average = (float) speed / speedCount;
                endingData.write(String.valueOf(average).getBytes(StandardCharsets.UTF_8));

                endingData.write(' ');
                endingData.write(String.valueOf(maxSpeed).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');
                endingData.write(String.valueOf(startPoint[0]).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');
                endingData.write(String.valueOf(startPoint[1]).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');
                endingData.write(String.valueOf(lastLongitude).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');
                endingData.write(String.valueOf(lastLatitude).getBytes(StandardCharsets.UTF_8));
                endingData.write("\n".getBytes(StandardCharsets.UTF_8));
                endingData.write(Arrays.toString(pauses.toArray()).getBytes(StandardCharsets.UTF_8));

                endingData.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

            index++;

            try {
                FileOutputStream outputStream = new FileOutputStream(rootDir + "/index");
                outputStream.write((byte) index);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Reset values to default.
            timer = new Timer();
            isRecording = false;
            startPoint = new double[2];
            startingTime = 0;

            // Stopping background service
            stopService(intent);
            unbindService(serviceConnection);

            setupLastTripData();
        });

        pauseRecording.setOnClickListener(view -> {
            if (isServiceBounded) {
                backgroundService.stopListener();
            }

            view.setVisibility(View.GONE);

            resumeRecording.setVisibility(View.VISIBLE);

            long date = new Date().getTime();
            pauses.push(date);

            backgroundService.setPauses(pauses);
        });

        resumeRecording.setOnClickListener(view -> {
            if (!havePermissions) {
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        1
                );

                havePermissions = permissions.verifyPermissions(MainActivity.this);

                if (!havePermissions) return;
            }

            if (permissions.locationEnabled(MainActivity.this)) return;

            backgroundService.startListener();

            view.setVisibility(View.GONE);
            pauseRecording.setVisibility(View.VISIBLE);

            long date = new DurationManager()
                    .getMSDuration(
                            pauses.get(pauses.count - 1),
                            new Date().getTime()
                    );

            pauses.push(date);

            backgroundService.setPauses(pauses);
        });
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            BackgroundService.LocalBinder binder = (BackgroundService.LocalBinder) iBinder;
            backgroundService = binder.getService();
            isServiceBounded = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isServiceBounded = false;
        }
    };

    public boolean foregroundServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void locationUpdated(Location location, double bearing, double slope, double altMetric, float actualSpeed, float distance) {
        String duration = new DurationManager().getDurationFromStartingDate(startingTime);
        String direction = new DirectionManager().getDirection(location.getBearing());

        durationView.setText("Durée d'enregistrement :" + duration);
        directionView.setText("Direction : " + direction);
        penteView.setText("Pente : " + Math.round(slope) + "%");
        altitudeView.setText("Altitude : " + (int) altMetric + "m");

        speedView.setText(
                String.format(Locale.FRANCE, "Vitesse : %d km/h", (int) (actualSpeed * 3600 / 1000))
        );

        if (distance > 1000) {
            double d = (double) distance / 1000;
            distanceView.setText(
                    String.format(Locale.FRANCE, "Distance parcourue : %.2f km", d)
            );
        } else {
            distanceView.setText(
                    String.format(Locale.FRANCE, "Distance parcourue : %d m", (int) distance)
            );
        }
    }

    public void setupLastTripData() {
        if (this.index < 1) return;
        lastTrips.clear();

        for (int i = this.index - 1; i > this.index - 4; i--) {
            if (i < 0) break;

            File file = new File(rootDir + "/_temp/" + i + "/infos");
            try {
                Scanner myReader = new Scanner(file);
                if (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    String[] args = data.split(" ");

                    Date date = new Date();
                    date.setTime(Long.parseLong(args[0]));

                    SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.FRANCE);
                    String startTripDate = formatter.format(date);
                    startTripDate = startTripDate.replace(":", "h");

                    String duration = new DurationManager().getDuration(Integer.parseInt(args[2]));
                    Float distance = Float.parseFloat(args[3]) / 1000;

                    TripData tripData = new TripData(startTripDate, String.format(Locale.FRANCE,
                            "%.2f Km - %s",
                            distance,
                            duration
                    ));

                    lastTrips.add(tripData);
                }
                myReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        RecyclerView tripsContainer = findViewById(R.id.tripsContainer);
        RecyclerViewAdapter recyclerViewAdapter = new RecyclerViewAdapter(this.getApplicationContext(), lastTrips, this);
        tripsContainer.setAdapter(recyclerViewAdapter);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this.getApplicationContext()) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        };

        tripsContainer.setLayoutManager(linearLayoutManager);
    }

    @Override
    public void onItemClick(int position) {
        int elementIndex = index - position - 1;

        Intent intent = new Intent(MainActivity.this, DetailsTripsActivity.class);

        intent.putExtra("ID", elementIndex);

        startActivity(intent);
    }
}