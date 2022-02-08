package models.songbase;

import java.util.List;

//  {
//    "songs": [
//      2235,
//      2236
//    ],
//    "references": [],
//    "books": []
//  }
public class Destroyed {

    private List<Integer> songs;
    private List<Integer> references;
    private List<Integer> books;

    public Destroyed() {
    }

    public List<Integer> getSongs() {
        return songs;
    }

    public void setSongs(List<Integer> songs) {
        this.songs = songs;
    }

    public List<Integer> getReferences() {
        return references;
    }

    public void setReferences(List<Integer> references) {
        this.references = references;
    }

    public List<Integer> getBooks() {
        return books;
    }

    public void setBooks(List<Integer> books) {
        this.books = books;
    }
}
