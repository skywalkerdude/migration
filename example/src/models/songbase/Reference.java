package models.songbase;

//     {
//      "id": 1,
//      "song_id": 303,
//      "book_id": 1,
//      "index": "1"
//    },
public class Reference {

    private int id;
    private int song_id;
    private int book_id;
    private String index;

    public Reference() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSong_id() {
        return song_id;
    }

    public void setSong_id(int song_id) {
        this.song_id = song_id;
    }

    public int getBook_id() {
        return book_id;
    }

    public void setBook_id(int book_id) {
        this.book_id = book_id;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }
}
