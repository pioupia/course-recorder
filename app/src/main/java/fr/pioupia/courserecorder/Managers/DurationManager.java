package fr.pioupia.courserecorder.Managers;

import java.util.Date;

public class DurationManager {
    public String getDurationFromStartingDate(long start) {
        Date date = new Date();
        int duration = (int) ((date.getTime() - start) * 1e-3);

        return getDuration(duration);
    }
    public String getDuration(int duration) {
        int days = duration / 86400;
        duration -= days * 86400;

        int hours = duration / 3600;
        duration -= hours * 3600;

        int minutes = duration / 60;
        duration -= minutes * 60;

        String str = "";

        if (days > 0) {
            str += days + "j ";
        }

        if (hours > 0) {
            str += hours + " h";
        }

        if (minutes > 0) {
            str += minutes + " min";
        }

        if (duration > 0) {
            str += duration + " s";
        }

        return str;
    }

    public long getMSDuration(long deb, long end) {
        return end - deb;
    }

    public long getPathDuration(long start, long end, long[] pauses) {
        long removeDuration = 0;
        for (int i = 1; i < pauses.length; i += 2) {
            removeDuration += pauses[i];
        }
        return (end - start - removeDuration);
    }
}
