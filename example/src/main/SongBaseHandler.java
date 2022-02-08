package main;

import com.google.gson.Gson;
import models.songbase.SongBase;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

/**
 * Ingests and handles data from the songbase.io database.
 */
public class SongBaseHandler {

    private SongBaseHandler() {
    }

    private static SongBase ingestSongBaseData() throws FileNotFoundException {
        FileInputStream stream = new FileInputStream("songbase.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        return new Gson().fromJson(reader, SongBase.class);
    }

    public static void handle() throws FileNotFoundException {
        SongBase songBase = ingestSongBaseData();
        System.out.println(songBase.getSongCount());
    }
}
