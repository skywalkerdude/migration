package models;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import main.TextUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConvertedHymn {

    public String title;
    public String lyricsJson;
    public String category;
    public String subCategory;
    public String author;
    public String composer;
    public String key;
    public String time;
    public String meter;
    public String scriptures;
    public String hymnCode;
    public String musicJson;
    public String svgJson;
    public String pdfJson;
    public String languagesJson;
    public String relevantJson;
    public List<H4aKey> languages;
    public Set<Reference> languageReferences = new LinkedHashSet<>();

    public H4aKey parentHymn;

    public ConvertedHymn(String title, String lyricsJson, String category, String subCategory, String author,
                         String composer, String key, String time, String meter, String scriptures, String hymnCode,
                         String musicJson, String svgJson, String pdfJson, List<H4aKey> languages, H4aKey parentHymn) {
        this.title = title;
        this.lyricsJson = lyricsJson;
        this.category = category;
        this.subCategory = subCategory;
        this.author = author;
        this.composer = composer;
        this.key = key;
        this.time = time;
        this.meter = meter;
        this.scriptures = scriptures;
        this.hymnCode = hymnCode;
        this.musicJson = musicJson;
        this.svgJson = svgJson;
        this.pdfJson = pdfJson;
        this.languagesJson = null;
        this.relevantJson = null;
        this.languages = languages;
        this.parentHymn = parentHymn;
    }

    public ConvertedHymn(String title, String lyricsJson, String category, String subCategory, String author,
                         String composer, String key, String time, String meter, String scriptures, String hymnCode,
                         String musicJson, String svgJson, String pdfJson, String languagesJson, String relevantJson) {
        this.title = title;
        this.lyricsJson = lyricsJson;
        this.category = category;
        this.subCategory = subCategory;
        this.author = author;
        this.composer = composer;
        this.key = key;
        this.time = time;
        this.meter = meter;
        this.scriptures = scriptures;
        this.hymnCode = hymnCode;
        this.musicJson = musicJson;
        this.svgJson = svgJson;
        this.pdfJson = pdfJson;
        this.languagesJson = languagesJson;
        this.languageReferences = extractLanguageReferences();
        this.relevantJson = relevantJson;
        this.languages = new ArrayList<>();
        this.parentHymn = null;
    }

    public Set<Reference> extractLanguageReferences() {
        Languages languages = new Gson().fromJson(languagesJson, Languages.class);
        if (languages == null) {
            return new LinkedHashSet<>();
        }
        return languages
                .getData().stream()
                .map(datum ->
                             Reference.create(datum.getValue(), HymnalDbKey.extractFromPath(datum.getPath())))
                .collect(Collectors.toSet());
    }

    public List<Verse> getLyrics() {
        Type listOfVerses = new TypeToken<ArrayList<Verse>>() {}.getType();
        List<Verse> lyrics = new Gson().fromJson(lyricsJson, listOfVerses);
        if (lyrics == null) {
            throw new IllegalArgumentException("lyricsJson failed to parse: " + lyricsJson);
        }
        return lyrics;
    }

    @Override
    public String toString() {
        return "\ntitle: " + title + "\nlyricsJson: " + lyricsJson + "\ncategory: " + category + "\nsub_category: "
               + subCategory + "\nauthor: " + author + "\ncomposer: " + composer + "\nkey: " + key + "\ntime: " + time
               + "\nmeter: " + meter + "\nscriptures: " + scriptures + "\nhymn_code: " + hymnCode + "\nmusicJson: "
               + musicJson + "\nsvgJson: " + svgJson + "\npdfJson: " + pdfJson + "\nlanguagesJson: " + languagesJson
               + "\nrelevantJson: " + relevantJson + "\nlanguages: " + languages + "\n";
    }
}
