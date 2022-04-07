package models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

//    {
//      "verse_content": [
//        "Drink! A river pure and clear that’s flowing from the throne;",
//        "Eat! The tree of life with fruits abundant, richly grown;",
//        "Look! No need of lamp nor sun nor moon to keep it bright, for",
//        "  Here there is no night!"
//      ],
//      "verse_type": "verse"
//    }
public class Verse {

    @SerializedName(value = "verse_type") private String verseType;
    @SerializedName(value = "verse_content") private List<String> verseContent;
    private List<String> transliteration;

    public Verse() {
    }

    public String verseType() {
        return verseType;
    }

    public void setVerseType(String verseType) {
        this.verseType = verseType;
    }

    public List<String> verseContent() {
        return verseContent;
    }

    public void setVerseContent(List<String> verseContent) {
        this.verseContent = verseContent;
    }

    public List<String> transliteration() {
        return transliteration;
    }

    public void setTransliteration(List<String> transliteration) {
        this.transliteration = transliteration;
    }

    @Override
    public String toString() {
        return "Verse{" +
               "verseType='" + verseType + '\'' +
               ", verseContent=" + verseContent +
               ", transliteration=" + transliteration +
               '}';
    }
}
