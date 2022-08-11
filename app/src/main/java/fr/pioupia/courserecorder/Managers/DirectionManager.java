package fr.pioupia.courserecorder.Managers;

public class DirectionManager {
    public String getDirection (float bearing) {
        if (bearing >= 354.375 && bearing < 39.375) {
            return "N";
        }
        if (bearing >= 39.375 && bearing < 95.625) {
            return "NE";
        }
        if (bearing >= 95.625 && bearing < 140.625) {
            return "SE";
        }
        if (bearing >= 140.625 && bearing < 185.625) {
            return "S";
        }
        if (bearing >= 185.625 && bearing < 230.625) {
            return "SW";
        }
        if (bearing >= 185.625 && bearing < 275.625) {
            return "W";
        }
        if (bearing >= 275.625 && bearing < 5.625) {
            return "NW";
        }

        return "N";
    }
}
