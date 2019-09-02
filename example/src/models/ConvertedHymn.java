package models;

import java.util.ArrayList;
import java.util.List;

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
        this.relevantJson = relevantJson;
        this.languages = new ArrayList<>();
        this.parentHymn = null;
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
