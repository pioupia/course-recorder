package fr.pioupia.courserecorder.Activites;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import fr.pioupia.courserecorder.MainActivity;
import fr.pioupia.courserecorder.Managers.DurationManager;
import fr.pioupia.courserecorder.R;

public class DetailsTripsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

        int id = getIntent().getIntExtra("ID", 0);

        String rootDir = getApplicationInfo().dataDir + "/records/_temp/" + id + "/";

        File file = new File(rootDir + "infos");

        if (!file.exists()) {
            System.out.println("File does not exist.");
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            return;
        }

        copy(rootDir, "infos");
        copy(rootDir, "speeds");
        copy(rootDir, "cords");
        copy(rootDir, "alt");

        boolean hasInternetAvailable = isInternetAvailable();

        if (!hasInternetAvailable) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.internet_required))
                    .setMessage(getString(R.string.internet_required_text))
                    .setPositiveButton("Ok", null)
                    .setNegativeButton(getString(R.string.cancel), (paramDialogInterface, paramInt) -> this.startActivity(new Intent(this, MainActivity.class)))
                    .show();
        }


        TextView tripTitle = findViewById(R.id.tripTitle);
        TextView startingPointText = findViewById(R.id.startPositionText);
        TextView endingPointText = findViewById(R.id.endPositionText);
        TextView startingDate = findViewById(R.id.startText);
        TextView endingDate = findViewById(R.id.endText);
        TextView distanceText = findViewById(R.id.distanceText);
        TextView durationText = findViewById(R.id.durationText);
        TextView speedAverageText = findViewById(R.id.speedAverageText);
        TextView maxSpeedText = findViewById(R.id.maxSpeedText);
        TextView pauseDurationText = findViewById(R.id.pauseText);

        tripTitle.setText(
                getString(R.string.trip_number_contracted, id + 1)
        );

        try {
            Scanner myReader = new Scanner(file);

            if (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                String[] args = data.split(" ");

                Date start = new Date();
                start.setTime(Long.parseLong(args[0]));

                Date end = new Date();
                end.setTime(Long.parseLong(args[1]));

                SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy à HH:mm", Locale.FRANCE);
                String startTripDate = formatter.format(start);
                String endTripDate = formatter.format(end);

                startTripDate = startTripDate.replace(":", "h");
                endTripDate = endTripDate.replace(":", "h");

                String duration = DurationManager.getDuration(Integer.parseInt(args[2]));
                Float distance = Float.parseFloat(args[3]) / 1000;
                double average = Double.parseDouble(args[4]) * 3.6;
                double maxSpeed = Double.parseDouble(args[5]) * 3.6;

                double startLong = Double.parseDouble(args[6]);
                double startLat = Double.parseDouble(args[7]);
                double endLong = Double.parseDouble(args[8]);
                double endLat = Double.parseDouble(args[9]);

                if (hasInternetAvailable) {
                    try {
                        Geocoder geocoder = new Geocoder(DetailsTripsActivity.this, Locale.getDefault());
                        List<Address> locationStart = geocoder.getFromLocation(startLat, startLong, 1);
                        List<Address> locationEnd = geocoder.getFromLocation(endLat, endLong, 1);

                        if (locationStart.size() > 0) {
                            startingPointText.setText(
                                    getString(R.string.starting, locationStart.get(0).getAddressLine(0))
                            );
                        }

                        if (locationEnd.size() > 0) {
                            endingPointText.setText(
                                    getString(R.string.end, locationEnd.get(0).getAddressLine(0))
                            );
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                startingDate.setText(
                        getString(R.string.beginning_default, startTripDate)
                );
                endingDate.setText(
                        getString(R.string.ending_default, endTripDate)
                );

                durationText.setText(
                        getString(R.string.default_duration, duration)
                );

                distanceText.setText(
                        getString(R.string.default_distance, distance)
                );
                speedAverageText.setText(
                        getString(R.string.default_speed, average)
                );
                maxSpeedText.setText(
                        getString(R.string.default_max_speed, maxSpeed)
                );

                if (myReader.hasNextLine()) {
                    data = myReader.nextLine();
                    args = data.substring(1, data.length() - 1).split(", ");

                    int pauseDuration = 0;

                    for (int i = 1; i < args.length; i += 2) {
                        pauseDuration += Integer.parseInt(args[i]);
                    }

                    String pauseDurationString = DurationManager.getDuration((int) pauseDuration / 1000);

                    if (Objects.equals(pauseDurationString, "")) {
                        pauseDurationString = "0s !";
                    }

                    pauseDurationText.setText(
                            getString(R.string.default_break, pauseDurationString)
                    );
                }
            }
            myReader.close();
        } catch (IOException e) {
            e.printStackTrace();

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }
    }

    public boolean isInternetAvailable() {
        return getInetAddressByName("google.com") != null;
    }

    public static InetAddress getInetAddressByName(String name) {
        AsyncTask<String, Void, InetAddress> task = new AsyncTask<String, Void, InetAddress>() {

            @Override
            protected InetAddress doInBackground(String... params) {
                try {
                    return InetAddress.getByName(params[0]);
                } catch (UnknownHostException e) {
                    return null;
                }
            }
        };

        try {
            return task.execute(name).get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    public static void copy(String path, String fileName) {
        try {
            FileReader fr = new FileReader(path + fileName);

            String envPath = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS) + "/";
            System.out.println(envPath);

            new File(envPath + fileName + ".txt").createNewFile();

            FileWriter fw = new FileWriter(envPath + fileName + ".txt");
            String str = "";

            int i;

            while ((i = fr.read()) != -1) {
                str += (char) i;
            }

            System.out.println(str);

            fw.write(str);

            fr.close();
            fw.close();

            System.out.println(
                    "File reading and writing both done");
        }

        catch (IOException e) {
            e.printStackTrace();
            System.out.println(
                    "There are some IOException");
        }
    }
}
