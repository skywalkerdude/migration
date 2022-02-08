package models.songbase;

//    {
//      "id": 1,
//      "name": "Blue Songbook",
//      "lang": "english",
//      "slug": "blue_songbook"
//    },
public class Book {
    private int id;
    private String name;
    private String lang;
    private String slug;

    public Book() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}
