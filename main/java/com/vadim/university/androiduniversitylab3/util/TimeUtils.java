package com.vadim.university.androiduniversitylab3.util;

public class TimeUtils {
    public static String milliSecondsToTimer(long milliseconds){
        String finalTimerString = "";

        int minutes = (int)(milliseconds % (1000*60*60)) / (1000*60);
        int seconds = (int) ((milliseconds % (1000*60*60)) % (1000*60) / 1000);

        finalTimerString += minutes < 10 ? "0" + minutes + ":" : "" + minutes + ":";
        finalTimerString += seconds < 10 ? "0" + seconds : "" + seconds;

        return finalTimerString;
    }

    public static int getProgressPercentage(long currentDuration, long totalDuration){
        return (int) (((double)currentDuration)/totalDuration *100);
    }

    public static int progressToTimer(int progress, int totalDuration) {
        int currentDuration = 0;
        totalDuration = totalDuration / 1000;
        currentDuration = (int) ((((double)progress) / 100) * totalDuration);

        return currentDuration * 1000;
    }
}
