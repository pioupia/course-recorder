package fr.pioupia.courserecorder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import fr.pioupia.courserecorder.Managers.DurationManager;
import fr.pioupia.courserecorder.Managers.PermissionsManager;


public class MainActivity extends AppCompatActivity implements BackgroundService.ServiceCallback {
    private int index = 0;

    public Timer timer = new Timer();

    /* Files stream */
    public FileOutputStream speeds = null;
    public FileOutputStream coords = null;
    public FileOutputStream alt = null;

    /* Data in real time */
    public boolean isRecording = false;
    public double[] startPoint = new double[2];
    public long startingTime = 0;
    public double lastLatitude = 0;
    public double lastLongitude = 0;
    public float distance = 0;
    public int altMetric = 0;
    public int lastAlt = 0;
    public Array pauses = new Array(1);

    private final int speedCount = 1;
    private final int speed = 0;
    private final float maxSpeed = 0;

    public boolean havePermissions = false;
    public String rootDir = "";

    public boolean isServiceBounded = false;
    public BackgroundService backgroundService;

    /* View */
    public TextView durationView = null;
    public TextView distanceView = null;
    public TextView speedView = null;
    public TextView directionView = null;

    /* Background app */
    PermissionsManager permissions = new PermissionsManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(MainActivity.this, BackgroundService.class);

        rootDir = getApplicationInfo().dataDir + "/records";

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

                    /**
                     * On peut garder en mémoire jusqu'à 127 trajets maximum.
                     * Ensuite, on upload ça sur le serveur
                     * Le serveur traite les données
                     * il renvoie les données en JSON / autre
                     * On met 30 records / fichier (ou moins, tout dépend de la taille finale du fichier)
                     * Le nom des fichiers est un index
                     * Donc en gros, le fichier indexes contiendra :
                     * numéro du dernier fichier utilisé nombre d'enregistrements
                     * On a en gros l'architecture suivante :
                     * /records (dossier des données de trajets)
                     * /records/index (stockage du dernier index en cache)
                     * /records/_temp/{index} (dossier de stockage d'un trajet en cache à l'index {index})
                     *      - /speeds (fichier contenant les vitesses)
                     *      - /coords (fichier contenant les coordonnées)
                     *      - /alt    (fichier content les données altimétriques)
                     *      - /infos (fichier contenant les informations générales en JSON.
                     *          - Date de début (date), date de fin (date)
                     *          - (liste des pauses (ms)), durée (ms)
                     *          - Distance (m), vitesse moyenne (km/h), // vitesse max (km/h)
                     *          - Point de départ, point d'arrivé
                     * /records/data (dossier qui stock tous les trajets parse)
                     * /records/data/index (stockage du dernier index sous la forme : "index du dossier   numéro du record")
                     * /records/data/{index}/ (speeds/coords/alt/infos - sous forme de tableau), exemple avec speeds :
                     * index ...speeds
                     * 0 12 15.00 12.53 88.12 25.32 46.58
                     * 1 12 15.00 12.53 88.12 25.32 46.58
                     * 2 12 15.00 12.53 88.12 25.32 46.58
                     * 3 12 15.00 12.53 88.12 25.32 46.58
                     * 4 12 15.00 12.53 88.12 25.32 46.58
                     *
                     * Quand on lance l'enregistrement, les 3 derniers trajets s'en vont de la manière suivante :
                     * - Le premier pars avec un fondu de l'opacité vers la droite (~50px)
                     * - Le second pars avec ~50ms/100 de retard, avec un fondu de l'opacité, vers la droite (~50px) & vers le haut (~20px)
                     * - Le troisième pars avec ~100/200ms de retard, avec un fondu de l'opacité, vers la droite (~50px) & vers le haut (~40px)
                     */

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

        System.out.println("LAUNCH");

        havePermissions = this.permissions.verifyPermissions(MainActivity.this);
        System.out.println("Verifications passed !");

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

        RelativeLayout buttonContainer = findViewById(R.id.buttonsContainer);
        ImageView startRecording = findViewById(R.id.startRecording);
        ImageView stopRecording = findViewById(R.id.stopRecording);
        ImageView pauseRecording = findViewById(R.id.pauseRecording);
        ImageView resumeRecording = findViewById(R.id.resumeRecording);
        durationView = findViewById(R.id.duration);
        distanceView = findViewById(R.id.distance);
        speedView = findViewById(R.id.speed);
        directionView = findViewById(R.id.direction);


        startRecording.setOnClickListener(v -> {
            havePermissions = permissions.askPermissions(MainActivity.this, MainActivity.this, havePermissions);
            if (!havePermissions) return;

            if (permissions.locationEnabled(MainActivity.this)) return;

            System.out.println("Defined intent");

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

            Date date = new Date();

            startingTime = date.getTime();

            File file1 = new File(rootDir + "/_temp/" + index);

            if (!file1.exists()) {
                file1.mkdirs();
            }

            try {
                String tempDir = rootDir + "/_temp/" + index + "/";
                speeds = new FileOutputStream(tempDir + "speeds");
                coords = new FileOutputStream(tempDir + "coords");
                alt = new FileOutputStream(tempDir + "alt");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            isRecording = true;

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (isServiceBounded) {
                        System.out.println("set cb");
                        backgroundService.setCallback(MainActivity.this);
                        timer.cancel();
                        timer.purge();
                    }
                }
            }, 300, 100);
        });

        stopRecording.setOnClickListener(view -> {
            long endTime = new Date().getTime();

            // mLocationManager.removeUpdates(mLocationListener);

            // Stopping background service
            stopService(intent);
            unbindService(serviceConnection);

            // Purge interval
            timer.purge();

            // Reset states
            buttonContainer.setVisibility(View.GONE);
            resumeRecording.setVisibility(View.GONE);

            startRecording.setVisibility(View.VISIBLE);
            pauseRecording.setVisibility(View.VISIBLE);

            isRecording = false;
            if (speeds != null) {
                try {
                    speeds.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                speeds = null;
            }

            if (coords != null) {
                try {
                    coords.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                coords = null;
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

            try {
                FileOutputStream outputStream = new FileOutputStream(rootDir + "/index");
                outputStream.write((byte) index+1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        pauseRecording.setOnClickListener(view -> {
            stopService(intent);

            view.setVisibility(View.GONE);

            resumeRecording.setVisibility(View.VISIBLE);

            long date = new Date().getTime();
            pauses.push(date);
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

            /* SetCallback here */

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }


            view.setVisibility(View.GONE);
            pauseRecording.setVisibility(View.VISIBLE);

            long date = new DurationManager().getMSDuration(pauses.get(pauses.count - 1), new Date().getTime());
            pauses.push(date);
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

    public boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for(ActivityManager.RunningServiceInfo service: activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if(BackgroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void locationUpdated(Location location) {
        System.out.println(location);
    }
}