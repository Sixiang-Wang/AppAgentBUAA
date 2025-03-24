package com.example.myapplication.scripts;


import static com.example.myapplication.activity.MainActivity.TAG;

import android.util.Log;

public class utils {
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BLACK = "\u001B[30m";
    public static void printWithColor(String text, String color) {
        String colorCode;

        switch (color.toLowerCase()) {
            case "red":
                colorCode = RED;
                break;
            case "green":
                colorCode = GREEN;
                break;
            case "yellow":
                colorCode = YELLOW;
                break;
            case "blue":
                colorCode = BLUE;
                break;
            case "magenta":
                colorCode = MAGENTA;
                break;
            case "cyan":
                colorCode = CYAN;
                break;
            case "white":
                colorCode = WHITE;
                break;
            case "black":
                colorCode = BLACK;
                break;
            default:
                colorCode = "";
        }
        Log.d(TAG, text);
        System.out.println(colorCode + text + RESET);
    }
}
