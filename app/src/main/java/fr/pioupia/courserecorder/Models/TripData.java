package fr.pioupia.courserecorder.Models;

public class TripData {
    String title;
    String description;

    public TripData(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
