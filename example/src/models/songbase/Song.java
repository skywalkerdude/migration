package models.songbase;

//    {
//      "id": 965,
//      "title": "Glory, glory, glory, praise and adoration",
//      "lang": "english",
//      "lyrics": "1\n[G]Glory, glory, glo[D7]ry, [G]praise and adorati[D7]on!\n[C]Hear the anthems swelling [C]out thro' all [D]ete[A]rn[D7]ity!\n[G]Father, Son, and [D7]Spirit-[G]God in revelation-\n[G]Prostrate each [C]soul before the [G]De[D]it[G]y! \n\n2\nFather, source of glory, naming every fam'ly;\nAnd the Son upholding all by His almighty power; \nHoly Spirit, filling the vast scene of glory-\nO glorious Fulness, let our hearts adore!\n\n3\nGod supreme, we worship now in holy splendour, \nHead of the vast scene of bliss, before Thy face we fall!\nMajesty and greatness, glory, praise and power\nTo Thee belong, eternal Source of all!\n"
//    },

public class Song {

    private int id;
    private String title;
    private String lang;
    private String lyrics;

    public Song() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }
}
