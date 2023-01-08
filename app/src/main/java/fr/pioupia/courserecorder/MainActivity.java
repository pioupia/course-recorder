package fr.pioupia.courserecorder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import fr.pioupia.courserecorder.Adapters.TripsList.RecyclerViewSwipeController;
import fr.pioupia.courserecorder.Adapters.TripsList.SwipeControllerActions;
import fr.pioupia.courserecorder.Managers.DirectionManager;
import fr.pioupia.courserecorder.Managers.DirectoryManager;
import fr.pioupia.courserecorder.Managers.DurationManager;
import fr.pioupia.courserecorder.Managers.ForegroundServiceManager;
import fr.pioupia.courserecorder.Managers.IndexManager;
import fr.pioupia.courserecorder.Managers.PermissionsManager;
import fr.pioupia.courserecorder.Models.TripData;


public class MainActivity extends AppCompatActivity implements BackgroundService.ServiceCallback, RecyclerViewInterface {
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

    /* View */
    public TextView durationView = null;
    public TextView distanceView = null;
    public TextView speedView = null;
    public TextView directionView = null;
    public TextView altitudeView = null;
    public TextView penteView = null;

    public ArrayList<TripData> lastTrips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(MainActivity.this, BackgroundService.class);

        rootDir = getApplicationInfo().dataDir + "/records";
        IndexManager.setRootDir(rootDir);

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
        penteView = findViewById(R.id.slope);

        if (this.foregroundServiceRunning()) {
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ForegroundServiceManager.restoreData(
                    activityManager, MainActivity.this, intent, this,
                    statsContainer, buttonContainer, tripsContainer, stopRecording, startRecording,
                    resumeRecording, pauseRecording);
        } else {
            File file = new File(rootDir);

            if (!file.exists()) {
                file.mkdir();
            }

            IndexManager.init();

            setupLastTripData();

            havePermissions = PermissionsManager.verifyPermissions(MainActivity.this);

            if (!havePermissions) {
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        1
                );

                havePermissions = PermissionsManager.verifyPermissions(MainActivity.this);
            }
        }


        startRecording.setOnClickListener(v -> {
            havePermissions = PermissionsManager.askPermissions(MainActivity.this, MainActivity.this, havePermissions);
            if (!havePermissions) return;

            if (PermissionsManager.locationEnabled(MainActivity.this)) return;

            if (!this.foregroundServiceRunning()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }

            bindService(intent, ForegroundServiceManager.serviceConnection, Context.BIND_AUTO_CREATE);

            v.setVisibility(View.GONE);
            buttonContainer.setVisibility(View.VISIBLE);

            statsContainer.setVisibility(View.VISIBLE);
            tripsContainer.setVisibility(View.GONE);

            Date date = new Date();

            startingTime = date.getTime();

            File file1 = new File(rootDir + "/_temp/" + IndexManager.getIndex());

            if (!file1.exists()) {
                file1.mkdirs();
            }

            try {
                String tempDir = rootDir + "/_temp/" + IndexManager.getIndex() + "/";
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
                    if (ForegroundServiceManager.isIsServiceBounded()) {
                        ForegroundServiceManager.getBackgroundService().setEssentialData(MainActivity.this, speeds, cords, alt);
                        timer.cancel();
                        timer.purge();
                    }
                }
            }, 300, 100);
        });

        stopRecording.setOnClickListener(view -> {
            long endTime = new Date().getTime();
            BackgroundService backgroundService = ForegroundServiceManager.getBackgroundService();
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
                long date = DurationManager.getMSDuration(pauses.get(pauses.count - 1), endTime);
                pauses.push(date);
            }

            float distance = backgroundService.distance;
            int speedCount = backgroundService.speedCount;
            double speed = backgroundService.speed;
            double maxSpeed = backgroundService.maxSpeed;
            double lastLatitude = backgroundService.lastLatitude;
            double lastLongitude = backgroundService.lastLongitude;

            try {
                FileOutputStream endingData = new FileOutputStream(rootDir + "/_temp/" + IndexManager.getIndex() + "/infos");
                endingData.write(String.valueOf(startingTime).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');
                endingData.write(String.valueOf(endTime).getBytes(StandardCharsets.UTF_8));
                endingData.write(' ');

                int duration = (int) ((DurationManager.getPathDuration(startingTime, endTime, pauses.toArray())) * 1e-3);
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

            IndexManager.incIndex();
            IndexManager.save();

            // Reset values to default.
            timer = new Timer();
            isRecording = false;
            startPoint = new double[2];
            startingTime = 0;

            // Stopping background service
            stopService(intent);
            unbindService(ForegroundServiceManager.serviceConnection);

            setupLastTripData();
        });

        pauseRecording.setOnClickListener(view -> {
            BackgroundService backgroundService = ForegroundServiceManager.getBackgroundService();
            if (ForegroundServiceManager.isIsServiceBounded()) {
                backgroundService.stopListener();
            }

            view.setVisibility(View.GONE);

            resumeRecording.setVisibility(View.VISIBLE);

            long date = new Date().getTime();
            pauses.push(date);

            backgroundService.setPauses(pauses);
        });

        resumeRecording.setOnClickListener(view -> {
            BackgroundService backgroundService = ForegroundServiceManager.getBackgroundService();

            if (!havePermissions) {
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        1
                );

                havePermissions = PermissionsManager.verifyPermissions(MainActivity.this);

                if (!havePermissions) return;
            }

            if (PermissionsManager.locationEnabled(MainActivity.this)) return;

            backgroundService.startListener();

            view.setVisibility(View.GONE);
            pauseRecording.setVisibility(View.VISIBLE);

            long date = DurationManager.getMSDuration(
                    pauses.get(pauses.count - 1),
                    new Date().getTime()
            );

            pauses.push(date);

            backgroundService.setPauses(pauses);
        });
    }

    public boolean foregroundServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void locationUpdated(@NonNull Location location, double bearing, double slope, double altMetric, float actualSpeed, float distance) {
        String duration = DurationManager.getDurationFromStartingDate(startingTime);
        String direction = DirectionManager.getDirection(location.getBearing());


        durationView.setText(
                getString(R.string.record_duration, duration)
        );
        directionView.setText(
                getString(R.string.direction, direction)
        );
        penteView.setText(
                getString(R.string.slope, Math.round(slope) + "%")
        );
        altitudeView.setText(
                getString(R.string.altitude, altMetric)
        );
        speedView.setText(
                getString(R.string.speed, actualSpeed * 3600 / 1000)
        );

        if (distance > 1000) {
            double d = (double) distance / 1000;
            distanceView.setText(
                    getString(R.string.distance_traveled, d + " Km")
            );
        } else {
            distanceView.setText(
                    getString(R.string.distance_traveled, ((int) distance) + " m")
            );
        }
    }

    public void setupLastTripData() {
        int index = IndexManager.getIndex();
        if (index < 1) return;
        lastTrips.clear();

        for (int i = index - 1; i > index - 4; i--) {
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

                    String duration = DurationManager.getDuration(Integer.parseInt(args[2]));
                    Float distance = Float.parseFloat(args[3]) / 1000;

                    TripData tripData = new TripData(startTripDate, String.format(Locale.ENGLISH,
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

        RecyclerViewSwipeController swipeController = new RecyclerViewSwipeController(
                getApplicationContext(),
                new SwipeControllerActions() {
                    @Override
                    public void onRightClicked(int position) {
                        lastTrips.remove(position);
                        recyclerViewAdapter.notifyItemRemoved(position);
                        recyclerViewAdapter.notifyItemRangeChanged(position, recyclerViewAdapter.getItemCount());


                        // Deleting the item in directories
                        int index = IndexManager.getIndex();
                        int positionInDirectory = index - position - 1;
                        File file = new File(rootDir + "/_temp/" + positionInDirectory + "/");
                        if (!file.exists()) return;

                        try {
                            DirectoryManager.deleteDirectory(file);

                            int minimum = 0;
                            for (int i = 0; i < index; i++) {
                                File dir = new File(rootDir + "/_temp/" + i + "/");

                                if (!dir.exists()) minimum++;
                                else break;
                            }

                            int gap = minimum;
                            for (int i = minimum; i < index; i++) {
                                File dir = new File(rootDir + "/_temp/" + i + "/");

                                if (!dir.exists()) {
                                    gap++;
                                    continue;
                                }

                                dir.renameTo(new File(rootDir + "/_temp/" + (i - gap) + "/"));
                            }

                            IndexManager.setIndex(index - gap);
                            IndexManager.save();

                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(getString(R.string.delete_record_success))
                                    .setMessage(
                                            String.format(
                                                    "%s %s",
                                                    getString(R.string.trip_number, positionInDirectory + 1),
                                                    getString(R.string.successfully_deleted)
                                            )
                                    )
                                    .setPositiveButton("OK", null)
                                    .show();

                            MainActivity.this.setupLastTripData();
                        } catch (Error ignored) {
                        }
                    }
                }
        );
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeController);
        itemTouchHelper.attachToRecyclerView(tripsContainer);

        tripsContainer.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                swipeController.onDraw(c);
            }
        });

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
        int elementIndex = IndexManager.getIndex() - position - 1;

        Intent intent = new Intent(MainActivity.this, DetailsTripsActivity.class);

        intent.putExtra("ID", elementIndex);

        startActivity(intent);
    }
}