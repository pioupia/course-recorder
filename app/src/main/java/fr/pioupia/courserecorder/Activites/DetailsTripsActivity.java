package fr.pioupia.courserecorder.Activites;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

import fr.pioupia.courserecorder.Array;
import fr.pioupia.courserecorder.MainActivity;
import fr.pioupia.courserecorder.Managers.DurationManager;
import fr.pioupia.courserecorder.Models.TripData;
import fr.pioupia.courserecorder.R;

public class DetailsTripsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

        int id = getIntent().getIntExtra("ID", 0);

        String rootDir = getApplicationInfo().dataDir + "/records/_temp/";

        File file = new File(rootDir + id + "/infos");

        if (!file.exists()) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            return;
        }

        TextView startingDate = findViewById(R.id.startText);
        TextView endingDate = findViewById(R.id.endText);
        TextView distanceText = findViewById(R.id.distanceText);
        TextView durationText = findViewById(R.id.durationText);
        TextView speedAverageText = findViewById(R.id.speedAverageText);
        TextView maxSpeedText = findViewById(R.id.maxSpeedText);
        TextView pauseDurationText = findViewById(R.id.pauseText);



        try {
            Scanner myReader = new Scanner(file);

            if (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                String[] args = data.split(" ");

                Date start = new Date();
                start.setTime(Long.parseLong(args[0]));

                Date end = new Date();
                end.setTime(Long.parseLong(args[0]));

                SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy à HH:mm", Locale.FRANCE);
                String startTripDate = formatter.format(start);
                String endTripDate = formatter.format(end);

                startTripDate = startTripDate.replace(":", "h");
                endTripDate = endTripDate.replace(":", "h");

                String duration = new DurationManager().getDuration(Integer.parseInt(args[2]));
                Float distance = Float.parseFloat(args[3]) / 1000;
                double average = Float.parseFloat(args[4]) * 3.6;
                double maxSpeed = Float.parseFloat(args[5]) * 3.6;

                startingDate.setText(
                        "• Début : " + startTripDate
                );
                endingDate.setText(
                        "• Fin : " + endTripDate
                );
                durationText.setText(
                        "• Durée : " + duration
                );
                distanceText.setText(
                        String.format(Locale.FRANCE, "• Distance : %.2f Km", distance)
                );
                speedAverageText.setText(
                        String.format(Locale.FRANCE, "• Vitesse moyenne : %.1f km/h", average)
                );
                maxSpeedText.setText(
                        String.format(Locale.FRANCE, "• Vitesse max : %.1f km/h", maxSpeed)
                );

                if (myReader.hasNextLine()) {
                    data = myReader.nextLine();
                    args = data.substring(1, data.length() - 1).split(", ");

                    int pauseDuration = 0;

                    for (int i = 1; i < args.length; i += 2) {
                        pauseDuration += Integer.parseInt(args[i]);
                    }

                    String pauseDurationString = new DurationManager().getDuration((int) pauseDuration / 1000);

                    pauseDurationText.setText(
                            String.format(
                                    Locale.FRANCE,
                                    "• Pause : %s",
                                    pauseDurationString
                            )
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
}
